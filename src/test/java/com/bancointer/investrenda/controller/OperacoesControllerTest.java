package com.bancointer.investrenda.controller;

import com.bancointer.investrenda.dto.ItemFilaMorta;
import com.bancointer.investrenda.exception.NaoEncontradoException;
import com.bancointer.investrenda.messaging.MensagemAplicacaoSolicitada;
import com.bancointer.investrenda.service.EstadoProcessamentoFila;
import com.bancointer.investrenda.service.ServicoFilaMorta;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OperacoesController nao tem um service proprio para a maior parte dos
 * endpoints - fala direto com AmqpAdmin, StringRedisTemplate e o estado em
 * memoria do toggle de simulacao da fila; a excecao e a DLQ, onde
 * ServicoFilaMorta concentra a logica de peek/reprocessamento (mockado
 * aqui, nao ha canal AMQP de verdade em um teste de controller).
 */
@WebMvcTest(OperacoesController.class)
class OperacoesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AmqpAdmin amqpAdmin;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private EstadoProcessamentoFila estadoProcessamentoFila;

    @MockBean
    private ServicoFilaMorta servicoFilaMorta;

    @Test
    void deveReportarStatusDaFilaQuandoFilasExistem() throws Exception {
        Properties propriedadesFilaAplicacoes = new Properties();
        propriedadesFilaAplicacoes.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 3);
        propriedadesFilaAplicacoes.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, 1);

        Properties propriedadesFilaMorta = new Properties();
        propriedadesFilaMorta.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 0);
        propriedadesFilaMorta.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, 0);

        when(amqpAdmin.getQueueProperties("investrenda.aplicacoes.fila")).thenReturn(propriedadesFilaAplicacoes);
        when(amqpAdmin.getQueueProperties("investrenda.aplicacoes.fila.morta")).thenReturn(propriedadesFilaMorta);
        when(estadoProcessamentoFila.isSimularFalha()).thenReturn(false);

        mockMvc.perform(get("/operacoes/fila"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.dados.aplicacoes.existe").value(true))
                .andExpect(jsonPath("$.dados.aplicacoes.quantidadeMensagens").value(3))
                .andExpect(jsonPath("$.dados.aplicacoes.quantidadeConsumidores").value(1))
                .andExpect(jsonPath("$.dados.filaMorta.quantidadeMensagens").value(0))
                .andExpect(jsonPath("$.dados.simulandoFalha").value(false));
    }

    @Test
    void deveReportarFilaInexistenteQuandoRabbitRetornaNulo() throws Exception {
        when(amqpAdmin.getQueueProperties("investrenda.aplicacoes.fila")).thenReturn(null);
        when(amqpAdmin.getQueueProperties("investrenda.aplicacoes.fila.morta")).thenReturn(null);

        mockMvc.perform(get("/operacoes/fila"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.aplicacoes.existe").value(false))
                .andExpect(jsonPath("$.dados.aplicacoes.quantidadeMensagens").value(0));
    }

    @Test
    void deveAtivarSimulacaoDeFalhaNaFila() throws Exception {
        when(estadoProcessamentoFila.isSimularFalha()).thenReturn(true);

        mockMvc.perform(put("/operacoes/fila/simular-falha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.dados.simulandoFalha").value(true))
                .andExpect(jsonPath("$.mensagem").value("Simulacao de falha na fila ativada"));

        verify(estadoProcessamentoFila).setSimularFalha(true);
    }

    @Test
    void deveListarMensagensDaDlq() throws Exception {
        ItemFilaMorta item = new ItemFilaMorta(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("300.00"));
        when(servicoFilaMorta.listar()).thenReturn(List.of(item));

        mockMvc.perform(get("/operacoes/fila/dlq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.dados[0].aplicacaoId").value(item.aplicacaoId().toString()))
                .andExpect(jsonPath("$.dados[0].valor").value(300.00));
    }

    @Test
    void deveReprocessarMensagemDaDlqComNovoValor() throws Exception {
        UUID aplicacaoId = UUID.randomUUID();
        MensagemAplicacaoSolicitada reenviada = new MensagemAplicacaoSolicitada(
                aplicacaoId, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("150.00"));
        when(servicoFilaMorta.reprocessar(eq(aplicacaoId), eq(new BigDecimal("150.00")))).thenReturn(reenviada);

        mockMvc.perform(post("/operacoes/fila/dlq/reprocessar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aplicacaoId\": \"" + aplicacaoId + "\", \"novoValor\": 150.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.dados.valor").value(150.00))
                .andExpect(jsonPath("$.mensagem").value("Mensagem reprocessada e reenviada para a fila principal"));
    }

    @Test
    void deveRetornar404QuandoAplicacaoIdNaoEstaNaDlq() throws Exception {
        UUID aplicacaoId = UUID.randomUUID();
        when(servicoFilaMorta.reprocessar(eq(aplicacaoId), any()))
                .thenThrow(new NaoEncontradoException("Mensagem nao encontrada na dead-letter queue: " + aplicacaoId));

        mockMvc.perform(post("/operacoes/fila/dlq/reprocessar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aplicacaoId\": \"" + aplicacaoId + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.sucesso").value(false));
    }

    @Test
    void deveRetornarBadRequestQuandoAplicacaoIdNoCorpoNaoEUuidValido() throws Exception {
        mockMvc.perform(post("/operacoes/fila/dlq/reprocessar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aplicacaoId\": \"nao-e-um-uuid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.sucesso").value(false));

        verifyNoInteractions(servicoFilaMorta);
    }

    @Test
    void deveReportarStatusDoCache() throws Exception {
        UUID produtoId = UUID.randomUUID();
        String chaveProduto = "produto::" + produtoId;

        when(stringRedisTemplate.hasKey("produtos::SimpleKey []")).thenReturn(true);
        when(stringRedisTemplate.getExpire(eq("produtos::SimpleKey []"), eq(TimeUnit.SECONDS))).thenReturn(540L);
        when(stringRedisTemplate.keys("produto::*")).thenReturn(Set.of(chaveProduto));
        when(stringRedisTemplate.getExpire(eq(chaveProduto), eq(TimeUnit.SECONDS))).thenReturn(300L);

        mockMvc.perform(get("/operacoes/cache"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.listaProdutosCacheada").value(true))
                .andExpect(jsonPath("$.dados.listaProdutosTtlSegundos").value(540))
                .andExpect(jsonPath("$.dados.itensCacheProduto[0].produtoId").value(produtoId.toString()))
                .andExpect(jsonPath("$.dados.itensCacheProduto[0].ttlSegundos").value(300));
    }

    @Test
    void deveReportarCacheVazioQuandoNadaCacheado() throws Exception {
        when(stringRedisTemplate.hasKey("produtos::SimpleKey []")).thenReturn(false);
        when(stringRedisTemplate.keys("produto::*")).thenReturn(Set.of());

        mockMvc.perform(get("/operacoes/cache"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.listaProdutosCacheada").value(false))
                .andExpect(jsonPath("$.dados.listaProdutosTtlSegundos").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.dados.itensCacheProduto").isEmpty());
    }
}
