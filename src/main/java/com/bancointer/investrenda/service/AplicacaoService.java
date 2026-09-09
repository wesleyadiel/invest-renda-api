package com.bancointer.investrenda.service;

import com.bancointer.investrenda.domain.Aplicacao;
import com.bancointer.investrenda.domain.Conta;
import com.bancointer.investrenda.domain.Produto;
import com.bancointer.investrenda.domain.enums.StatusAplicacao;
import com.bancointer.investrenda.dto.RequisicaoAplicacao;
import com.bancointer.investrenda.dto.RespostaAplicacao;
import com.bancointer.investrenda.exception.NaoEncontradoException;
import com.bancointer.investrenda.exception.NegocioException;
import com.bancointer.investrenda.messaging.MensagemAplicacaoSolicitada;
import com.bancointer.investrenda.repository.AplicacaoRepository;
import com.bancointer.investrenda.repository.ContaRepository;
import com.bancointer.investrenda.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Fluxo assincrono: a requisicao apenas valida os dados de entrada, registra
 * a aplicacao como PENDENTE e publica na fila. O debito de saldo e a
 * conclusao da aplicacao acontecem no {@link ConsumidorRequisicaoAplicacao},
 * que roda em transacao propria para evitar corrida entre aplicacoes
 * concorrentes na mesma conta.
 */
@Service
@RequiredArgsConstructor
public class AplicacaoService {

    private final AplicacaoRepository aplicacaoRepository;
    private final ContaRepository contaRepository;
    private final ProdutoRepository produtoRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${investrenda.rabbitmq.exchange}")
    private String nomeExchange;

    @Value("${investrenda.rabbitmq.chave-roteamento}")
    private String chaveRoteamento;

    @Transactional
    public RespostaAplicacao solicitarAplicacao(RequisicaoAplicacao requisicao) {
        Conta conta = contaRepository.buscarPorCpf(requisicao.cpf())
                .orElseThrow(() -> new NaoEncontradoException("Conta nao encontrada para o cpf: " + requisicao.cpf()));

        Produto produto = produtoRepository.findById(requisicao.produtoId())
                .orElseThrow(() -> new NaoEncontradoException("Produto nao encontrado: " + requisicao.produtoId()));

        if (!produto.isAtivo()) {
            throw new NegocioException("Produto indisponivel para aplicacao");
        }

        if (requisicao.valor().compareTo(produto.getInvestimentoMinimo()) < 0) {
            throw new NegocioException("Valor abaixo do minimo exigido pelo produto: " + produto.getInvestimentoMinimo());
        }

        Aplicacao aplicacao = Aplicacao.builder()
                .conta(conta)
                .produto(produto)
                .valor(requisicao.valor())
                .status(StatusAplicacao.PENDENTE)
                .build();

        aplicacao = aplicacaoRepository.saveAndFlush(aplicacao);

        MensagemAplicacaoSolicitada mensagem = new MensagemAplicacaoSolicitada(
                aplicacao.getId(), conta.getId(), produto.getId(), requisicao.valor());

        rabbitTemplate.convertAndSend(nomeExchange, chaveRoteamento, mensagem);

        return RespostaAplicacao.from(aplicacao);
    }

    @Transactional(readOnly = true)
    public RespostaAplicacao buscarPorId(UUID id) {
        Aplicacao aplicacao = aplicacaoRepository.findById(id)
                .orElseThrow(() -> new NaoEncontradoException("Aplicacao nao encontrada: " + id));
        return RespostaAplicacao.from(aplicacao);
    }

    @Transactional(readOnly = true)
    public List<RespostaAplicacao> listarTodas() {
        return aplicacaoRepository.buscarTodasOrdenadasPorDataCriacaoDesc().stream()
                .map(RespostaAplicacao::from)
                .toList();
    }
}
