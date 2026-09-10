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
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

// O debito de saldo e a conclusao da aplicacao NAO acontecem aqui - so
// registra como PENDENTE e publica na fila; quem debita e conclui e o
// ConsumidorRequisicaoAplicacao, em transacao propria, para nao serializar
// aplicacoes concorrentes de contas diferentes atras desta chamada HTTP.
@Slf4j
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

        log.info("Aplicacao solicitada e publicada na fila",
                kv("aplicacaoId", aplicacao.getId()), kv("contaId", conta.getId()),
                kv("produtoId", produto.getId()), kv("valor", requisicao.valor()));

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
