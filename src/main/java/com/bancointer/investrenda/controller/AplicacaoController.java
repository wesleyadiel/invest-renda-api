package com.bancointer.investrenda.controller;

import com.bancointer.investrenda.dto.RequisicaoAplicacao;
import com.bancointer.investrenda.dto.RespostaApi;
import com.bancointer.investrenda.dto.RespostaAplicacao;
import com.bancointer.investrenda.service.AplicacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Tag(name = "Aplicações")
@RestController
@RequestMapping("/aplicacoes")
@RequiredArgsConstructor
public class AplicacaoController {

    private final AplicacaoService aplicacaoService;

    @Operation(summary = "Solicitar aplicacao",
            description = "Valida sincronamente o que nao depende de concorrencia em saldo (conta existe, "
                    + "produto existe/ativo, valor >= minimo) e publica na fila. O saldo **ainda nao foi "
                    + "debitado** na resposta - isso acontece de forma assincrona logo em seguida, processado "
                    + "por um worker. Consulte GET /aplicacoes/{id} para acompanhar o status saindo de PENDENTE.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Aplicacao aceita e enviada para a fila, em PENDENTE.",
                    content = @Content(schema = @Schema(implementation = RespostaAplicacao.class))),
            @ApiResponse(responseCode = "400", description = "Payload invalido."),
            @ApiResponse(responseCode = "404", description = "CPF sem conta, ou produto inexistente.", content = @Content(examples = @ExampleObject(value = """
                    {"sucesso": false, "dados": null, "mensagem": "Conta nao encontrada para o cpf: <cpf>", "erros": []}"""))),
            @ApiResponse(responseCode = "422", description = "Produto inativo, ou valor abaixo do minimo exigido "
                    + "pelo produto.", content = @Content(examples = @ExampleObject(value = """
                    {"sucesso": false, "dados": null,
                     "mensagem": "Valor abaixo do minimo exigido pelo produto: 100.00", "erros": []}""")))
    })
    @PostMapping
    public ResponseEntity<RespostaApi<RespostaAplicacao>> aplicar(@Valid @RequestBody RequisicaoAplicacao requisicao) {
        RespostaAplicacao resposta = aplicacaoService.solicitarAplicacao(requisicao);
        return ResponseEntity.accepted()
                .location(URI.create("/aplicacoes/" + resposta.id()))
                .body(RespostaApi.criarSucesso(resposta, "Aplicacao enviada para processamento"));
    }

    @Operation(summary = "Consultar status da aplicacao",
            description = "Use em polling ate `status` sair de PENDENTE - nao existe webhook/callback nesta versao.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aplicacao encontrada.",
                    content = @Content(schema = @Schema(implementation = RespostaAplicacao.class))),
            @ApiResponse(responseCode = "404", description = "Aplicacao nao encontrada.")
    })
    @GetMapping("/{id}")
    public ResponseEntity<RespostaApi<RespostaAplicacao>> buscarPorId(
            @Parameter(description = "Identificador da aplicacao.") @PathVariable UUID id) {
        RespostaAplicacao aplicacao = aplicacaoService.buscarPorId(id);
        return ResponseEntity.ok(RespostaApi.criarSucesso(aplicacao, "Aplicacao encontrada"));
    }

    @Operation(summary = "Listar aplicacoes",
            description = "Lista todas as aplicacoes, ordenadas por data de criacao decrescente (mais "
                    + "recente primeiro). `contaCpf`/`produtoNome` ja vem resolvidos, sem precisar cruzar "
                    + "com GET /contas ou GET /produtos manualmente.")
    @ApiResponse(responseCode = "200", description = "Lista de aplicacoes (pode vir vazia).",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = RespostaAplicacao.class))))
    @GetMapping
    public ResponseEntity<RespostaApi<List<RespostaAplicacao>>> listar() {
        List<RespostaAplicacao> aplicacoes = aplicacaoService.listarTodas();
        return ResponseEntity.ok(RespostaApi.criarSucesso(aplicacoes, "Aplicacoes listadas com sucesso"));
    }
}
