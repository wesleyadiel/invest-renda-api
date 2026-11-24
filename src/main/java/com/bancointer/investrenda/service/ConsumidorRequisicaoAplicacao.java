package com.bancointer.investrenda.service;

import com.bancointer.investrenda.domain.Aplicacao;
import com.bancointer.investrenda.domain.Conta;
import com.bancointer.investrenda.domain.Produto;
import com.bancointer.investrenda.domain.enums.StatusAplicacao;
import com.bancointer.investrenda.exception.FalhaProcessamentoAplicacaoException;
import com.bancointer.investrenda.exception.SaldoInsuficienteException;
import com.bancointer.investrenda.messaging.MensagemAplicacaoSolicitada;
import com.bancointer.investrenda.repository.AplicacaoRepository;
import com.bancointer.investrenda.repository.ContaRepository;
import com.bancointer.investrenda.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static net.logstash.logback.argument.StructuredArguments.kv;

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
        MDC.put("aplicacaoId", mensagem.aplicacaoId().toString());
        try {
            processarComMdc(mensagem);
        } finally {
            MDC.remove("aplicacaoId");
        }
    }

    private void processarComMdc(MensagemAplicacaoSolicitada mensagem) {
        if (estadoProcessamentoFila.isSimularFalha()) {
            log.warn("Falha simulada no processamento da fila");
            throw new FalhaProcessamentoAplicacaoException(
                    "Falha simulada via toggle de teste (PUT /operacoes/fila/simular-falha)");
        }

        Aplicacao aplicacao = aplicacaoRepository.findById(mensagem.aplicacaoId())
                .orElseThrow(() -> new IllegalStateException("Aplicacao nao encontrada: " + mensagem.aplicacaoId()));

        if (aplicacao.getStatus() != StatusAplicacao.PENDENTE) {
            log.info("Mensagem duplicada ignorada", kv("status", aplicacao.getStatus()));
            return;
        }

        Conta conta = contaRepository.buscarPorIdParaAtualizacao(mensagem.contaId())
                .orElseThrow(() -> new IllegalStateException("Conta nao encontrada: " + mensagem.contaId()));
        Produto produto = produtoRepository.findById(mensagem.produtoId())
                .orElseThrow(() -> new IllegalStateException("Produto nao encontrado: " + mensagem.produtoId()));

        // Normalmente igual ao valor de criacao da aplicacao, mas pode
        // divergir quando a mensagem foi reprocessada da DLQ com novoValor
        // (POST /operacoes/fila/dlq/reprocessar) - o registro precisa
        // refletir o valor de fato debitado, senao fica inconsistente com
        // o saldo movimentado.
        aplicacao.setValor(mensagem.valor());

        if (!conta.temSaldoSuficiente(mensagem.valor())) {

            throw new SaldoInsuficienteException("Saldo insuficiente");
//            aplicacao.setStatus(StatusAplicacao.FALHOU);
//            aplicacao.setMotivoFalha("Saldo insuficiente na conta");
//            aplicacaoRepository.save(aplicacao);
//            log.warn("Aplicacao falhou: saldo insuficiente", kv("contaId", conta.getId()), kv("valor", mensagem.valor()));
//            return;
        }

        conta.debitar(mensagem.valor());
        contaRepository.save(conta);

        aplicacao.setStatus(StatusAplicacao.CONCLUIDA);
        aplicacao.setTaxaAplicada(produto.getTaxaAnual());
        aplicacao.setDataVencimento(LocalDate.now().plusMonths(produto.getPrazoMeses()));
        aplicacaoRepository.save(aplicacao);

        log.info("Aplicacao concluida", kv("contaId", conta.getId()), kv("valor", mensagem.valor()));
    }
}
