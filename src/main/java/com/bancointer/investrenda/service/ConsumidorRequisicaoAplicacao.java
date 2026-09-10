package com.bancointer.investrenda.service;

import com.bancointer.investrenda.domain.Aplicacao;
import com.bancointer.investrenda.domain.Conta;
import com.bancointer.investrenda.domain.Produto;
import com.bancointer.investrenda.domain.enums.StatusAplicacao;
import com.bancointer.investrenda.exception.FalhaProcessamentoAplicacaoException;
import com.bancointer.investrenda.messaging.MensagemAplicacaoSolicitada;
import com.bancointer.investrenda.repository.AplicacaoRepository;
import com.bancointer.investrenda.repository.ContaRepository;
import com.bancointer.investrenda.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Consome as solicitacoes de aplicacao da fila e efetiva o investimento:
 * trava a conta (lock pessimista) para evitar corrida entre aplicacoes
 * concorrentes, confere saldo e debita de acordo com os dados atuais do
 * produto (taxa e prazo), mantendo a conta e a aplicacao consistentes na
 * mesma transacao.
 *
 * <p>Se {@link EstadoProcessamentoFila#isSimularFalha()} estiver ativo,
 * lanca falha antes de tocar o banco, de forma deterministica, para
 * exercitar o retry do listener (spring.rabbitmq.listener.simple.retry no
 * application.yml, mesmo padrao 3x/1s-2s-4s usado no circuit breaker da
 * cotacao do dolar) e - apos as tentativas se esgotarem - o roteamento
 * automatico para a dead-letter queue (fila configurada com
 * x-dead-letter-exchange/routing-key em ConfiguracaoRabbitMQ).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumidorRequisicaoAplicacao {

    private final AplicacaoRepository aplicacaoRepository;
    private final ContaRepository contaRepository;
    private final ProdutoRepository produtoRepository;
    private final EstadoProcessamentoFila estadoProcessamentoFila;

    @RabbitListener(queues = "${investrenda.rabbitmq.fila}")
    @Transactional
    public void processar(MensagemAplicacaoSolicitada mensagem) {
        if (estadoProcessamentoFila.isSimularFalha()) {
            throw new FalhaProcessamentoAplicacaoException(
                    "Falha simulada via toggle de teste (PUT /operacoes/fila/simular-falha)");
        }

        Aplicacao aplicacao = aplicacaoRepository.findById(mensagem.aplicacaoId())
                .orElseThrow(() -> new IllegalStateException("Aplicacao nao encontrada: " + mensagem.aplicacaoId()));

        if (aplicacao.getStatus() != StatusAplicacao.PENDENTE) {
            log.info("Aplicacao {} ja processada com status {}, ignorando mensagem duplicada",
                    aplicacao.getId(), aplicacao.getStatus());
            return;
        }

        Conta conta = contaRepository.buscarPorIdParaAtualizacao(mensagem.contaId())
                .orElseThrow(() -> new IllegalStateException("Conta nao encontrada: " + mensagem.contaId()));
        Produto produto = produtoRepository.findById(mensagem.produtoId())
                .orElseThrow(() -> new IllegalStateException("Produto nao encontrado: " + mensagem.produtoId()));

        // Normalmente igual ao valor com que a aplicacao foi criada - mas
        // pode divergir quando a mensagem foi reprocessada da DLQ com um
        // novoValor (POST /operacoes/fila/dlq/reprocessar). O registro da
        // aplicacao precisa refletir o valor de fato debitado, senao o
        // saldo debitado e o valor exibido na aplicacao ficam inconsistentes.
        aplicacao.setValor(mensagem.valor());

        if (!conta.temSaldoSuficiente(mensagem.valor())) {
            aplicacao.setStatus(StatusAplicacao.FALHOU);
            aplicacao.setMotivoFalha("Saldo insuficiente na conta");
            aplicacaoRepository.save(aplicacao);
            log.warn("Aplicacao {} falhou: saldo insuficiente na conta {}", aplicacao.getId(), conta.getId());
            return;
        }

        conta.debitar(mensagem.valor());
        contaRepository.save(conta);

        aplicacao.setStatus(StatusAplicacao.CONCLUIDA);
        aplicacao.setTaxaAplicada(produto.getTaxaAnual());
        aplicacao.setDataVencimento(LocalDate.now().plusMonths(produto.getPrazoMeses()));
        aplicacaoRepository.save(aplicacao);

        log.info("Aplicacao {} concluida: conta {} debitada em {}", aplicacao.getId(), conta.getId(), mensagem.valor());
    }
}
