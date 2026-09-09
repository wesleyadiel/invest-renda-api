package com.bancointer.investrenda.controller;

import com.bancointer.investrenda.dto.RequisicaoToggleIntegracao;
import com.bancointer.investrenda.dto.RespostaApi;
import com.bancointer.investrenda.dto.RespostaCotacaoDolar;
import com.bancointer.investrenda.dto.StatusIntegracaoExterna;
import com.bancointer.investrenda.service.ServicoIntegracaoExterna;
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
@RestController
@RequestMapping("/integracoes")
@RequiredArgsConstructor
public class IntegracaoExternaController {

    private final ServicoIntegracaoExterna servico;

    @GetMapping("/cotacao-dolar")
    public ResponseEntity<RespostaApi<RespostaCotacaoDolar>> consultarCotacaoDolar() {
        RespostaCotacaoDolar resposta = servico.consultarCotacaoDolar();
        return ResponseEntity.ok(RespostaApi.criarSucesso(resposta, "Cotacao consultada com sucesso"));
    }

    @GetMapping("/status")
    public ResponseEntity<RespostaApi<StatusIntegracaoExterna>> status() {
        return ResponseEntity.ok(RespostaApi.criarSucesso(servico.status(), "Status da integracao consultado com sucesso"));
    }

    @PutMapping("/habilitar")
    public ResponseEntity<RespostaApi<StatusIntegracaoExterna>> habilitar(@Valid @RequestBody RequisicaoToggleIntegracao requisicao) {
        servico.habilitar(requisicao.ativo());
        String mensagem = requisicao.ativo() ? "Integracao habilitada" : "Integracao desabilitada";
        return ResponseEntity.ok(RespostaApi.criarSucesso(servico.status(), mensagem));
    }

    @PutMapping("/simular-falha")
    public ResponseEntity<RespostaApi<StatusIntegracaoExterna>> simularFalha(@Valid @RequestBody RequisicaoToggleIntegracao requisicao) {
        servico.simularFalha(requisicao.ativo());
        String mensagem = requisicao.ativo() ? "Simulacao de falha ativada" : "Simulacao de falha desativada";
        return ResponseEntity.ok(RespostaApi.criarSucesso(servico.status(), mensagem));
    }
}
