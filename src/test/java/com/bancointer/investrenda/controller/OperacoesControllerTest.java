package com.bancointer.investrenda.controller;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OperacoesController nao tem um service proprio - fala direto com
 * AmqpAdmin e StringRedisTemplate, entao sao esses dois que precisam ser
 * mockados aqui (nao ha bean de service para substituir).
 */
@WebMvcTest(OperacoesController.class)
class OperacoesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AmqpAdmin amqpAdmin;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

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

        mockMvc.perform(get("/operacoes/fila"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.dados.aplicacoes.existe").value(true))
                .andExpect(jsonPath("$.dados.aplicacoes.quantidadeMensagens").value(3))
                .andExpect(jsonPath("$.dados.aplicacoes.quantidadeConsumidores").value(1))
                .andExpect(jsonPath("$.dados.filaMorta.quantidadeMensagens").value(0));
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
