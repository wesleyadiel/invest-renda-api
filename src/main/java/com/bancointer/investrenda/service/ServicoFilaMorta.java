package com.bancointer.investrenda.service;

import com.bancointer.investrenda.dto.ItemFilaMorta;
import com.bancointer.investrenda.exception.NaoEncontradoException;
import com.bancointer.investrenda.messaging.MensagemAplicacaoSolicitada;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Espia (peek) e reprocessa manualmente mensagens presas na dead-letter
 * queue - ferramenta de observabilidade/correcao manual para o painel de
 * DLQ do frontend, nao faz parte do fluxo automatico de negocio (esse fica
 * todo em {@link ConsumidorRequisicaoAplicacao}).
 *
 * <p>RabbitMQ nao tem "ver sem remover" nativo: {@link #listar()} usa
 * {@code basicGet} para drenar a fila (ack pendente) e devolve tudo com
 * {@code basicNack(requeue=true)} antes de retornar - o "peek" e so
 * aparente, mas o efeito liquido e nao-destrutivo. {@link #reprocessar}
 * faz o mesmo drain, mas so devolve as mensagens que nao sao o alvo; a
 * escolhida e retirada de vez (ack) e republicada na fila principal.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoFilaMorta {

    private static final int LIMITE_MENSAGENS = 50;

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${investrenda.rabbitmq.fila-morta}")
    private String nomeFilaMorta;

    @Value("${investrenda.rabbitmq.exchange}")
    private String nomeExchange;

    @Value("${investrenda.rabbitmq.chave-roteamento}")
    private String chaveRoteamento;

    public List<ItemFilaMorta> listar() {
        return rabbitTemplate.execute(channel -> {
            List<GetResponse> respostas = drenar(channel);
            List<ItemFilaMorta> itens = new ArrayList<>();
            for (GetResponse resposta : respostas) {
                MensagemAplicacaoSolicitada mensagem = desserializar(resposta);
                itens.add(new ItemFilaMorta(mensagem.aplicacaoId(), mensagem.contaId(), mensagem.produtoId(), mensagem.valor()));
            }
            devolverTodas(channel, respostas);
            return itens;
        });
    }

    public MensagemAplicacaoSolicitada reprocessar(UUID aplicacaoId, BigDecimal novoValor) {
        return rabbitTemplate.execute(channel -> {
            List<GetResponse> respostas = drenar(channel);

            GetResponse alvo = null;
            MensagemAplicacaoSolicitada mensagemAlvo = null;
            for (GetResponse resposta : respostas) {
                MensagemAplicacaoSolicitada mensagem = desserializar(resposta);
                if (mensagem.aplicacaoId().equals(aplicacaoId)) {
                    alvo = resposta;
                    mensagemAlvo = mensagem;
                    break;
                }
            }

            // Devolve todas as demais para a DLQ (requeue); a alvo - se
            // encontrada - e retirada definitivamente (ack) daqui.
            for (GetResponse resposta : respostas) {
                if (resposta == alvo) {
                    channel.basicAck(resposta.getEnvelope().getDeliveryTag(), false);
                } else {
                    channel.basicNack(resposta.getEnvelope().getDeliveryTag(), false, true);
                }
            }

            if (mensagemAlvo == null) {
                log.warn("Reprocessamento solicitado para mensagem ausente na DLQ", kv("aplicacaoId", aplicacaoId));
                throw new NaoEncontradoException("Mensagem nao encontrada na dead-letter queue: " + aplicacaoId);
            }

            MensagemAplicacaoSolicitada reenviada = novoValor != null
                    ? new MensagemAplicacaoSolicitada(mensagemAlvo.aplicacaoId(), mensagemAlvo.contaId(), mensagemAlvo.produtoId(), novoValor)
                    : mensagemAlvo;

            rabbitTemplate.convertAndSend(nomeExchange, chaveRoteamento, reenviada);
            log.info("Mensagem reprocessada da DLQ",
                    kv("aplicacaoId", aplicacaoId), kv("valorOriginal", mensagemAlvo.valor()), kv("valorReenviado", reenviada.valor()));
            return reenviada;
        });
    }

    private List<GetResponse> drenar(Channel channel) throws IOException {
        List<GetResponse> respostas = new ArrayList<>();
        GetResponse resposta;
        while (respostas.size() < LIMITE_MENSAGENS && (resposta = channel.basicGet(nomeFilaMorta, false)) != null) {
            respostas.add(resposta);
        }
        return respostas;
    }

    private void devolverTodas(Channel channel, List<GetResponse> respostas) throws IOException {
        for (GetResponse resposta : respostas) {
            channel.basicNack(resposta.getEnvelope().getDeliveryTag(), false, true);
        }
    }

    private MensagemAplicacaoSolicitada desserializar(GetResponse resposta) {
        try {
            return objectMapper.readValue(resposta.getBody(), MensagemAplicacaoSolicitada.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Mensagem na dead-letter queue em formato inesperado", e);
        }
    }
}
