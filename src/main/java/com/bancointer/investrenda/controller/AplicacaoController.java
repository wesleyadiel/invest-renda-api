package com.bancointer.investrenda.controller;

import com.bancointer.investrenda.dto.RequisicaoAplicacao;
import com.bancointer.investrenda.dto.RespostaApi;
import com.bancointer.investrenda.dto.RespostaAplicacao;
import com.bancointer.investrenda.service.AplicacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/aplicacoes")
@RequiredArgsConstructor
public class AplicacaoController {

    private final AplicacaoService aplicacaoService;

    @PostMapping
    public ResponseEntity<RespostaApi<RespostaAplicacao>> aplicar(@Valid @RequestBody RequisicaoAplicacao requisicao) {
        RespostaAplicacao resposta = aplicacaoService.solicitarAplicacao(requisicao);
        return ResponseEntity.accepted()
                .location(URI.create("/aplicacoes/" + resposta.id()))
                .body(RespostaApi.criarSucesso(resposta, "Aplicacao enviada para processamento"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RespostaApi<RespostaAplicacao>> buscarPorId(@PathVariable UUID id) {
        RespostaAplicacao aplicacao = aplicacaoService.buscarPorId(id);
        return ResponseEntity.ok(RespostaApi.criarSucesso(aplicacao, "Aplicacao encontrada"));
    }

    @GetMapping
    public ResponseEntity<RespostaApi<List<RespostaAplicacao>>> listar() {
        List<RespostaAplicacao> aplicacoes = aplicacaoService.listarTodas();
        return ResponseEntity.ok(RespostaApi.criarSucesso(aplicacoes, "Aplicacoes listadas com sucesso"));
    }
}
