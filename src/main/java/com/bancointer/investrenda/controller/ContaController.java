package com.bancointer.investrenda.controller;

import com.bancointer.investrenda.dto.RequisicaoConta;
import com.bancointer.investrenda.dto.RespostaApi;
import com.bancointer.investrenda.dto.RespostaConta;
import com.bancointer.investrenda.service.ContaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/contas")
@RequiredArgsConstructor
public class ContaController {

    private final ContaService contaService;

    @PostMapping
    public ResponseEntity<RespostaApi<RespostaConta>> criar(@Valid @RequestBody RequisicaoConta requisicao) {
        RespostaConta criada = contaService.criar(requisicao);
        return ResponseEntity.created(URI.create("/contas/" + criada.cpf()))
                .body(RespostaApi.criarSucesso(criada, "Conta criada com sucesso"));
    }

    @GetMapping("/{cpf}")
    public ResponseEntity<RespostaApi<RespostaConta>> buscarPorCpf(@PathVariable String cpf) {
        RespostaConta conta = contaService.buscarPorCpf(cpf);
        return ResponseEntity.ok(RespostaApi.criarSucesso(conta, "Conta encontrada"));
    }

    @GetMapping
    public ResponseEntity<RespostaApi<List<RespostaConta>>> listar() {
        List<RespostaConta> contas = contaService.listarTodas();
        return ResponseEntity.ok(RespostaApi.criarSucesso(contas, "Contas listadas com sucesso"));
    }
}
