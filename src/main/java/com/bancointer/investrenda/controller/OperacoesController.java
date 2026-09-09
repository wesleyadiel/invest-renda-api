package com.bancointer.investrenda.controller;

import com.bancointer.investrenda.dto.ItemCacheProduto;
import com.bancointer.investrenda.dto.RespostaApi;
import com.bancointer.investrenda.dto.RespostaStatusCache;
import com.bancointer.investrenda.dto.RespostaStatusFila;
import com.bancointer.investrenda.dto.StatusFila;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Endpoints so para observabilidade/validacao manual da API - nao fazem
 * parte do dominio de negocio. Toda informacao vem consultada ao vivo do
 * RabbitMQ e do Redis (nao ha nada em cache/estado proprio aqui), para
 * refletir exatamente o que esta acontecendo nesses dois sistemas.
 */
@Tag(name = "Observabilidade")
@RestController
@RequestMapping("/operacoes")
@RequiredArgsConstructor
public class OperacoesController {

    private static final String CHAVE_LISTA_PRODUTOS = "produtos::SimpleKey []";
    private static final String PREFIXO_DETALHE_PRODUTO = "produto::";

    private final AmqpAdmin amqpAdmin;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${investrenda.rabbitmq.fila}")
    private String nomeFila;

    @Value("${investrenda.rabbitmq.fila-morta}")
    private String nomeFilaMorta;

    @Operation(summary = "Status da fila de aplicacoes",
            description = "Consulta ao vivo no RabbitMQ - quantidade de mensagens e consumidores na fila "
                    + "principal e na dead-letter queue.")
    @ApiResponse(responseCode = "200", description = "Status consultado com sucesso.",
            content = @Content(schema = @Schema(implementation = RespostaStatusFila.class)))
    @GetMapping("/fila")
    public RespostaApi<RespostaStatusFila> statusFila() {
        RespostaStatusFila status = new RespostaStatusFila(statusFila(nomeFila), statusFila(nomeFilaMorta));
        return RespostaApi.criarSucesso(status, "Status da fila consultado com sucesso");
    }

    private StatusFila statusFila(String nome) {
        Properties propriedades = amqpAdmin.getQueueProperties(nome);
        if (propriedades == null) {
            return new StatusFila(nome, false, 0, 0);
        }
        long quantidadeMensagens = ((Number) propriedades.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).longValue();
        int quantidadeConsumidores = ((Number) propriedades.get(RabbitAdmin.QUEUE_CONSUMER_COUNT)).intValue();
        return new StatusFila(nome, true, quantidadeMensagens, quantidadeConsumidores);
    }

    @Operation(summary = "Status do cache de produtos",
            description = "Consulta ao vivo no Redis - se a listagem esta cacheada e quais detalhes de "
                    + "produto individuais estao em cache, com o TTL restante de cada um.")
    @ApiResponse(responseCode = "200", description = "Status consultado com sucesso.",
            content = @Content(schema = @Schema(implementation = RespostaStatusCache.class)))
    @GetMapping("/cache")
    public RespostaApi<RespostaStatusCache> statusCache() {
        boolean listaCacheada = Boolean.TRUE.equals(stringRedisTemplate.hasKey(CHAVE_LISTA_PRODUTOS));
        Long listaTtl = listaCacheada ? stringRedisTemplate.getExpire(CHAVE_LISTA_PRODUTOS, TimeUnit.SECONDS) : null;

        Set<String> chavesProduto = stringRedisTemplate.keys(PREFIXO_DETALHE_PRODUTO + "*");
        List<ItemCacheProduto> itens = (chavesProduto == null ? Set.<String>of() : chavesProduto).stream()
                .map(chave -> new ItemCacheProduto(
                        UUID.fromString(chave.substring(PREFIXO_DETALHE_PRODUTO.length())),
                        stringRedisTemplate.getExpire(chave, TimeUnit.SECONDS)))
                .sorted(Comparator.comparing(item -> item.produtoId().toString()))
                .toList();

        RespostaStatusCache status = new RespostaStatusCache(listaCacheada, listaTtl, itens);
        return RespostaApi.criarSucesso(status, "Status do cache consultado com sucesso");
    }
}
