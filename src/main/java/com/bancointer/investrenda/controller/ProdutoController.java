package com.bancointer.investrenda.controller;

import com.bancointer.investrenda.dto.RequisicaoProduto;
import com.bancointer.investrenda.dto.RespostaApi;
import com.bancointer.investrenda.dto.RespostaDetalheProduto;
import com.bancointer.investrenda.dto.RespostaRelatorioProduto;
import com.bancointer.investrenda.dto.RespostaResumoProduto;
import com.bancointer.investrenda.service.ProdutoService;
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

@Tag(name = "Produtos")
@RestController
@RequestMapping("/produtos")
@RequiredArgsConstructor
public class ProdutoController {

    private final ProdutoService produtoService;

    @Operation(summary = "Cadastrar produto",
            description = "Cria um produto de renda fixa e invalida o cache da listagem.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Produto criado.",
                    content = @Content(schema = @Schema(implementation = RespostaDetalheProduto.class))),
            @ApiResponse(responseCode = "400", description = "Payload invalido (campo obrigatorio ausente, "
                    + "taxa/valor minimo <= 0, prazo <= 0, etc.).", content = @Content(examples = @ExampleObject(value = """
                    {"sucesso": false, "dados": null, "mensagem": "Erro de validacao",
                     "erros": ["taxaAnual: must be greater than 0"]}""")))
    })
    @PostMapping
    public ResponseEntity<RespostaApi<RespostaDetalheProduto>> criar(@Valid @RequestBody RequisicaoProduto requisicao) {
        RespostaDetalheProduto criado = produtoService.criar(requisicao);
        return ResponseEntity.created(URI.create("/produtos/" + criado.id()))
                .body(RespostaApi.criarSucesso(criado, "Produto criado com sucesso"));
    }

    @Operation(summary = "Editar produto",
            description = "Substitui os dados do produto pelo payload enviado (PUT - representacao completa). "
                    + "Invalida tanto o cache da listagem quanto o cache de detalhe desse produto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produto atualizado.",
                    content = @Content(schema = @Schema(implementation = RespostaDetalheProduto.class))),
            @ApiResponse(responseCode = "400", description = "Payload invalido."),
            @ApiResponse(responseCode = "404", description = "Produto nao encontrado.", content = @Content(examples = @ExampleObject(value = """
                    {"sucesso": false, "dados": null, "mensagem": "Produto nao encontrado: <id>", "erros": []}""")))
    })
    @PutMapping("/{id}")
    public ResponseEntity<RespostaApi<RespostaDetalheProduto>> atualizar(
            @Parameter(description = "Identificador do produto.") @PathVariable UUID id,
            @Valid @RequestBody RequisicaoProduto requisicao) {
        RespostaDetalheProduto atualizado = produtoService.atualizar(id, requisicao);
        return ResponseEntity.ok(RespostaApi.criarSucesso(atualizado, "Produto atualizado com sucesso"));
    }

    @Operation(summary = "Listar produtos",
            description = "Lista todos os produtos (resumo). Passa por cache Redis (chave `produtos`, TTL 10 min).")
    @ApiResponse(responseCode = "200", description = "Lista de produtos (pode vir vazia).",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = RespostaResumoProduto.class))))
    @GetMapping
    public ResponseEntity<RespostaApi<List<RespostaResumoProduto>>> listar() {
        List<RespostaResumoProduto> produtos = produtoService.listarResumo();
        return ResponseEntity.ok(RespostaApi.criarSucesso(produtos, "Produtos listados com sucesso"));
    }

    @Operation(summary = "Consultar detalhe do produto",
            description = "Passa por cache Redis (chave `produto::{id}`, TTL 10 min).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produto encontrado.",
                    content = @Content(schema = @Schema(implementation = RespostaDetalheProduto.class))),
            @ApiResponse(responseCode = "404", description = "Produto nao encontrado.")
    })
    @GetMapping("/{id}")
    public ResponseEntity<RespostaApi<RespostaDetalheProduto>> buscarDetalhe(
            @Parameter(description = "Identificador do produto.") @PathVariable UUID id) {
        RespostaDetalheProduto produto = produtoService.buscarDetalhe(id);
        return ResponseEntity.ok(RespostaApi.criarSucesso(produto, "Produto encontrado"));
    }

    @Operation(summary = "Relatorio de aplicacoes do produto",
            description = "Traz o produto junto com todas as aplicacoes (compras) que ja o utilizaram, mais "
                    + "recentes primeiro. Nao passa por cache - o status de cada aplicacao pode mudar a "
                    + "qualquer momento de forma assincrona.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Relatorio gerado (lista de aplicacoes pode vir vazia).",
                    content = @Content(schema = @Schema(implementation = RespostaRelatorioProduto.class))),
            @ApiResponse(responseCode = "404", description = "Produto nao encontrado.")
    })
    @GetMapping("/{id}/relatorio")
    public ResponseEntity<RespostaApi<RespostaRelatorioProduto>> gerarRelatorioAplicacoes(
            @Parameter(description = "Identificador do produto.") @PathVariable UUID id) {
        RespostaRelatorioProduto relatorio = produtoService.gerarRelatorioAplicacoes(id);
        return ResponseEntity.ok(RespostaApi.criarSucesso(relatorio, "Relatorio gerado com sucesso"));
    }

    @Operation(summary = "Excluir produto",
            description = "Remove o produto e invalida os mesmos caches do PUT. So e permitido se nao houver "
                    + "nenhuma aplicacao associada a esse produto - o historico de aplicacoes nao pode ficar "
                    + "com uma referencia quebrada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produto excluido.", content = @Content(examples = @ExampleObject(value = """
                    {"sucesso": true, "dados": null, "mensagem": "Produto excluido com sucesso", "erros": []}"""))),
            @ApiResponse(responseCode = "404", description = "Produto nao encontrado."),
            @ApiResponse(responseCode = "422", description = "Existe pelo menos uma aplicacao associada a este "
                    + "produto - exclusao bloqueada.", content = @Content(examples = @ExampleObject(value = """
                    {"sucesso": false, "dados": null,
                     "mensagem": "Nao e possivel excluir um produto com aplicacoes associadas", "erros": []}""")))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<RespostaApi<Void>> excluir(
            @Parameter(description = "Identificador do produto.") @PathVariable UUID id) {
        produtoService.excluir(id);
        return ResponseEntity.ok(RespostaApi.criarSucesso(null, "Produto excluido com sucesso"));
    }
}
