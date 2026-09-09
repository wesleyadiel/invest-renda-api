package com.bancointer.investrenda.controller;

import com.bancointer.investrenda.dto.RequisicaoToggleIntegracao;
import com.bancointer.investrenda.dto.RespostaApi;
import com.bancointer.investrenda.dto.RespostaCotacaoDolar;
import com.bancointer.investrenda.dto.StatusIntegracaoExterna;
import com.bancointer.investrenda.service.ServicoIntegracaoExterna;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Exemplo de chamada a uma API externa (cotacao do dolar) protegida por
 * feature toggle + retry + circuit breaker, e as rotas usadas para simular
 * falha e observar o comportamento sem depender da API externa de verdade
 * estar fora do ar.
 */
@Tag(name = "Integrações Externas")
@RestController
@RequestMapping("/integracoes")
@RequiredArgsConstructor
public class IntegracaoExternaController {

    private final ServicoIntegracaoExterna servico;

    @Operation(summary = "Consultar cotacao do dolar",
            description = "Chama a AwesomeAPI (economia.awesomeapi.com.br), protegida por retry com backoff "
                    + "exponencial (3 tentativas: 1s, 2s, 4s) e circuit breaker (Resilience4j).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cotacao consultada com sucesso.",
                    content = @Content(schema = @Schema(implementation = RespostaCotacaoDolar.class))),
            @ApiResponse(responseCode = "503", description = "Integracao desabilitada, simulacao de falha "
                    + "ativa com as tentativas esgotadas, ou circuit breaker aberto (falha rapida, sem sequer "
                    + "tentar a chamada).", content = @Content(examples = @ExampleObject(value = """
                    {"sucesso": false, "dados": null,
                     "mensagem": "Servico de cotacao indisponivel no momento, tente novamente em breve", "erros": []}""")))
    })
    @GetMapping("/cotacao-dolar")
    public ResponseEntity<RespostaApi<RespostaCotacaoDolar>> consultarCotacaoDolar() {
        RespostaCotacaoDolar resposta = servico.consultarCotacaoDolar();
        return ResponseEntity.ok(RespostaApi.criarSucesso(resposta, "Cotacao consultada com sucesso"));
    }

    @Operation(summary = "Status da integracao",
            description = "Estado atual do feature toggle, da simulacao de falha e do circuit breaker.")
    @ApiResponse(responseCode = "200", description = "Status consultado com sucesso.",
            content = @Content(schema = @Schema(implementation = StatusIntegracaoExterna.class)))
    @GetMapping("/status")
    public ResponseEntity<RespostaApi<StatusIntegracaoExterna>> status() {
        return ResponseEntity.ok(RespostaApi.criarSucesso(servico.status(), "Status da integracao consultado com sucesso"));
    }

    @Operation(summary = "Ligar/desligar a integracao (feature toggle)",
            description = "Estado em memoria - reseta para o valor do application.yml "
                    + "(`investrenda.integracaoexterna.habilitado`) a cada restart da aplicacao.")
    @ApiResponse(responseCode = "200", description = "Toggle atualizado.",
            content = @Content(schema = @Schema(implementation = StatusIntegracaoExterna.class)))
    @PutMapping("/habilitar")
    public ResponseEntity<RespostaApi<StatusIntegracaoExterna>> habilitar(@Valid @RequestBody RequisicaoToggleIntegracao requisicao) {
        servico.habilitar(requisicao.ativo());
        String mensagem = requisicao.ativo() ? "Integracao habilitada" : "Integracao desabilitada";
        return ResponseEntity.ok(RespostaApi.criarSucesso(servico.status(), mensagem));
    }

    @Operation(summary = "Simular falha na proxima consulta",
            description = "Com `ativo: true`, toda chamada a GET /integracoes/cotacao-dolar falha *antes* de "
                    + "tentar a API externa de verdade - deterministico, nao depende de rede. Util para "
                    + "observar ao vivo o retry e, com chamadas repetidas, o circuit breaker abrindo.")
    @ApiResponse(responseCode = "200", description = "Simulacao atualizada.",
            content = @Content(schema = @Schema(implementation = StatusIntegracaoExterna.class)))
    @PutMapping("/simular-falha")
    public ResponseEntity<RespostaApi<StatusIntegracaoExterna>> simularFalha(@Valid @RequestBody RequisicaoToggleIntegracao requisicao) {
        servico.simularFalha(requisicao.ativo());
        String mensagem = requisicao.ativo() ? "Simulacao de falha ativada" : "Simulacao de falha desativada";
        return ResponseEntity.ok(RespostaApi.criarSucesso(servico.status(), mensagem));
    }
}
