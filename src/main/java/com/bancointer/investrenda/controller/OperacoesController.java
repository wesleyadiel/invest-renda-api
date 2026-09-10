package com.bancointer.investrenda.controller;

import com.bancointer.investrenda.dto.ItemCacheProduto;
import com.bancointer.investrenda.dto.ItemFilaMorta;
import com.bancointer.investrenda.dto.RequisicaoReprocessarDlq;
import com.bancointer.investrenda.dto.RequisicaoToggleIntegracao;
import com.bancointer.investrenda.dto.RespostaApi;
import com.bancointer.investrenda.dto.RespostaStatusCache;
import com.bancointer.investrenda.dto.RespostaStatusFila;
import com.bancointer.investrenda.dto.StatusFila;
import com.bancointer.investrenda.messaging.MensagemAplicacaoSolicitada;
import com.bancointer.investrenda.service.EstadoProcessamentoFila;
import com.bancointer.investrenda.service.ServicoFilaMorta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
 * parte do dominio de negocio. A quantidade de mensagens e consumidores vem
 * consultada ao vivo do RabbitMQ e do Redis, para refletir exatamente o que
 * esta acontecendo nesses dois sistemas; a unica excecao e o toggle de
 * simulacao de falha da fila ({@link EstadoProcessamentoFila}), que e
 * estado em memoria proprio da API (mesmo padrao do toggle de simulacao do
 * circuit breaker em IntegracaoExternaController).
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
    private final EstadoProcessamentoFila estadoProcessamentoFila;
    private final ServicoFilaMorta servicoFilaMorta;

    @Value("${investrenda.rabbitmq.fila}")
    private String nomeFila;

    @Value("${investrenda.rabbitmq.fila-morta}")
    private String nomeFilaMorta;

    @Operation(summary = "Status da fila de aplicacoes",
            description = "Consulta ao vivo no RabbitMQ - quantidade de mensagens e consumidores na fila "
                    + "principal e na dead-letter queue - mais o estado (em memoria) do toggle de simulacao "
                    + "de falha do processamento.")
    @ApiResponse(responseCode = "200", description = "Status consultado com sucesso.",
            content = @Content(schema = @Schema(implementation = RespostaStatusFila.class)))
    @GetMapping("/fila")
    public RespostaApi<RespostaStatusFila> statusFila() {
        RespostaStatusFila status = new RespostaStatusFila(
                statusFila(nomeFila), statusFila(nomeFilaMorta), estadoProcessamentoFila.isSimularFalha());
        return RespostaApi.criarSucesso(status, "Status da fila consultado com sucesso");
    }

    @Operation(summary = "Simular falha no processamento da fila",
            description = "Com `ativo: true`, todo consumo de mensagem pelo "
                    + "ConsumidorRequisicaoAplicacao falha *antes* de tocar o banco - deterministico, nao "
                    + "depende de nenhum estado de negocio (conta/produto/saldo). Util para observar ao vivo "
                    + "o retry do listener (3 tentativas, backoff 1s/2s/4s) e, apos se esgotarem, a mensagem "
                    + "sendo roteada automaticamente para a dead-letter queue. Publique uma aplicacao "
                    + "(POST /aplicacoes) com a simulacao ativa e acompanhe GET /operacoes/fila enquanto o "
                    + "RabbitMQ tenta reprocessar. Lembre de desativar depois, senao toda aplicacao nova "
                    + "tambem vai para a dead-letter.")
    @ApiResponse(responseCode = "200", description = "Simulacao atualizada.",
            content = @Content(schema = @Schema(implementation = RespostaStatusFila.class)))
    @PutMapping("/fila/simular-falha")
    public RespostaApi<RespostaStatusFila> simularFalhaFila(@Valid @RequestBody RequisicaoToggleIntegracao requisicao) {
        estadoProcessamentoFila.setSimularFalha(requisicao.ativo());
        String mensagem = requisicao.ativo() ? "Simulacao de falha na fila ativada" : "Simulacao de falha na fila desativada";
        return RespostaApi.criarSucesso(statusFila().dados(), mensagem);
    }

    @Operation(summary = "Listar mensagens na dead-letter queue",
            description = "Espia (peek, nao remove) ate 50 mensagens presas na DLQ, para inspecao manual. Use "
                    + "`aplicacaoId` de um item da lista em POST /operacoes/fila/dlq/reprocessar.")
    @ApiResponse(responseCode = "200", description = "Lista consultada com sucesso (pode vir vazia).",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ItemFilaMorta.class))))
    @GetMapping("/fila/dlq")
    public RespostaApi<List<ItemFilaMorta>> listarDlq() {
        return RespostaApi.criarSucesso(servicoFilaMorta.listar(), "Mensagens da dead-letter queue listadas com sucesso");
    }

    @Operation(summary = "Reprocessar mensagem da dead-letter queue",
            description = "Retira a mensagem de `aplicacaoId` da DLQ e reenvia para a fila principal, de onde "
                    + "o ConsumidorRequisicaoAplicacao vai pega-la de novo normalmente. Informe `novoValor` "
                    + "para corrigir o valor antes de reenviar (ex: reduzir para caber no saldo disponivel); "
                    + "omita para reenviar com o valor original. Lembre de desativar a simulacao de falha "
                    + "(PUT /operacoes/fila/simular-falha) antes, senao a mensagem reenviada cai na DLQ de "
                    + "novo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mensagem reenviada para a fila principal.",
                    content = @Content(schema = @Schema(implementation = MensagemAplicacaoSolicitada.class))),
            @ApiResponse(responseCode = "404", description = "Nenhuma mensagem com esse aplicacaoId na DLQ no momento.")
    })
    @PostMapping("/fila/dlq/reprocessar")
    public RespostaApi<MensagemAplicacaoSolicitada> reprocessarDlq(@Valid @RequestBody RequisicaoReprocessarDlq requisicao) {
        MensagemAplicacaoSolicitada reenviada = servicoFilaMorta.reprocessar(requisicao.aplicacaoId(), requisicao.novoValor());
        return RespostaApi.criarSucesso(reenviada, "Mensagem reprocessada e reenviada para a fila principal");
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
