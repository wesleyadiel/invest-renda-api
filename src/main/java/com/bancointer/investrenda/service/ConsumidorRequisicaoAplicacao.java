package com.bancointer.investrenda.service;

import com.bancointer.investrenda.domain.Aplicacao;
import com.bancointer.investrenda.domain.Conta;
import com.bancointer.investrenda.domain.Produto;
import com.bancointer.investrenda.domain.enums.StatusAplicacao;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumidorRequisicaoAplicacao {

    private final AplicacaoRepository aplicacaoRepository;
    private final ContaRepository contaRepository;
    private final ProdutoRepository produtoRepository;

    @RabbitListener(queues = "${investrenda.rabbitmq.fila}")
    @Transactional
    public void processar(MensagemAplicacaoSolicitada mensagem) {
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
