package com.bancointer.investrenda.service;

import com.bancointer.investrenda.dto.ItemFilaMorta;
import com.bancointer.investrenda.exception.NaoEncontradoException;
import com.bancointer.investrenda.messaging.MensagemAplicacaoSolicitada;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RabbitTemplate.execute(ChannelCallback) e o unico jeito de falar direto
 * com o canal AMQP (basicGet/basicAck/basicNack), entao os testes aqui
 * simulam esse metodo executando o callback de verdade contra um Channel
 * mockado - do contrario nao haveria como exercitar a logica de peek/drain.
 */
@ExtendWith(MockitoExtension.class)
class ServicoFilaMortaTest {

    private static final String FILA_MORTA = "investrenda.aplicacoes.fila.morta";
    private static final String EXCHANGE = "investrenda.aplicacoes.exchange";
    private static final String ROTEAMENTO = "investrenda.aplicacoes.aplicar";

    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private Channel channel;

    private ServicoFilaMorta servico;

    @BeforeEach
    void setUp() {
        servico = new ServicoFilaMorta(rabbitTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(servico, "nomeFilaMorta", FILA_MORTA);
        ReflectionTestUtils.setField(servico, "nomeExchange", EXCHANGE);
        ReflectionTestUtils.setField(servico, "chaveRoteamento", ROTEAMENTO);

        when(rabbitTemplate.execute(any())).thenAnswer(invocation -> {
            ChannelCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(channel);
        });
    }

    private GetResponse mensagemNaFila(long deliveryTag, MensagemAplicacaoSolicitada mensagem) throws Exception {
        byte[] corpo = new ObjectMapper().writeValueAsBytes(mensagem);
        Envelope envelope = new Envelope(deliveryTag, false, EXCHANGE, ROTEAMENTO);
        return new GetResponse(envelope, mock(AMQP.BasicProperties.class), corpo, 0);
    }

    @Test
    void deveListarMensagensEDevolverTodasParaAFilaSemRemover() throws Exception {
        MensagemAplicacaoSolicitada mensagem = new MensagemAplicacaoSolicitada(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("300.00"));
        GetResponse resposta = mensagemNaFila(1L, mensagem);

        when(channel.basicGet(FILA_MORTA, false)).thenReturn(resposta, (GetResponse) null);

        List<ItemFilaMorta> itens = servico.listar();

        assertThat(itens).hasSize(1);
        assertThat(itens.get(0).aplicacaoId()).isEqualTo(mensagem.aplicacaoId());
        assertThat(itens.get(0).valor()).isEqualByComparingTo("300.00");
        verify(channel).basicNack(1L, false, true);
        verify(channel, never()).basicAck(eq(1L), anyBoolean());
    }

    @Test
    void deveReprocessarMensagemAlvoEDevolverAsDemais() throws Exception {
        MensagemAplicacaoSolicitada alvo = new MensagemAplicacaoSolicitada(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("300.00"));
        MensagemAplicacaoSolicitada outra = new MensagemAplicacaoSolicitada(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("50.00"));

        when(channel.basicGet(FILA_MORTA, false)).thenReturn(
                mensagemNaFila(1L, outra), mensagemNaFila(2L, alvo), null);

        MensagemAplicacaoSolicitada reenviada = servico.reprocessar(alvo.aplicacaoId(), new BigDecimal("150.00"));

        assertThat(reenviada.valor()).isEqualByComparingTo("150.00");
        assertThat(reenviada.aplicacaoId()).isEqualTo(alvo.aplicacaoId());
        verify(channel).basicAck(2L, false);
        verify(channel).basicNack(1L, false, true);
        verify(rabbitTemplate).convertAndSend(EXCHANGE, ROTEAMENTO, reenviada);
    }

    @Test
    void deveReprocessarComValorOriginalQuandoNovoValorNaoInformado() throws Exception {
        MensagemAplicacaoSolicitada alvo = new MensagemAplicacaoSolicitada(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("300.00"));

        when(channel.basicGet(FILA_MORTA, false)).thenReturn(mensagemNaFila(1L, alvo), (GetResponse) null);

        MensagemAplicacaoSolicitada reenviada = servico.reprocessar(alvo.aplicacaoId(), null);

        assertThat(reenviada).isEqualTo(alvo);
        verify(channel).basicAck(1L, false);
    }

    @Test
    void deveLancarNaoEncontradoEDevolverTudoQuandoAplicacaoIdNaoEstaNaDlq() throws Exception {
        MensagemAplicacaoSolicitada outra = new MensagemAplicacaoSolicitada(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("50.00"));

        when(channel.basicGet(FILA_MORTA, false)).thenReturn(mensagemNaFila(1L, outra), (GetResponse) null);

        assertThatThrownBy(() -> servico.reprocessar(UUID.randomUUID(), null))
                .isInstanceOf(NaoEncontradoException.class);

        verify(channel).basicNack(1L, false, true);
        verify(channel, never()).basicAck(eq(1L), anyBoolean());
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }
}
