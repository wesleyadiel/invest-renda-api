package com.bancointer.investrenda.controller;

import com.bancointer.investrenda.dto.RequisicaoProduto;
import com.bancointer.investrenda.dto.RespostaApi;
import com.bancointer.investrenda.dto.RespostaDetalheProduto;
import com.bancointer.investrenda.dto.RespostaResumoProduto;
import com.bancointer.investrenda.service.ProdutoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/produtos")
@RequiredArgsConstructor
public class ProdutoController {

    private final ProdutoService produtoService;

    @PostMapping
    public ResponseEntity<RespostaApi<RespostaDetalheProduto>> criar(@Valid @RequestBody RequisicaoProduto requisicao) {
        RespostaDetalheProduto criado = produtoService.criar(requisicao);
        return ResponseEntity.created(URI.create("/produtos/" + criado.id()))
                .body(RespostaApi.criarSucesso(criado, "Produto criado com sucesso"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RespostaApi<RespostaDetalheProduto>> atualizar(@PathVariable UUID id,
                                                                          @Valid @RequestBody RequisicaoProduto requisicao) {
        RespostaDetalheProduto atualizado = produtoService.atualizar(id, requisicao);
        return ResponseEntity.ok(RespostaApi.criarSucesso(atualizado, "Produto atualizado com sucesso"));
    }

    @GetMapping
    public ResponseEntity<RespostaApi<List<RespostaResumoProduto>>> listar() {
        List<RespostaResumoProduto> produtos = produtoService.listarResumo();
        return ResponseEntity.ok(RespostaApi.criarSucesso(produtos, "Produtos listados com sucesso"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RespostaApi<RespostaDetalheProduto>> buscarDetalhe(@PathVariable UUID id) {
        RespostaDetalheProduto produto = produtoService.buscarDetalhe(id);
        return ResponseEntity.ok(RespostaApi.criarSucesso(produto, "Produto encontrado"));
    }
}
