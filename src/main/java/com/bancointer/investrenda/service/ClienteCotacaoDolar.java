package com.bancointer.investrenda.service;

import com.bancointer.investrenda.dto.RespostaCotacaoDolar;
import com.bancointer.investrenda.exception.FalhaConsultaCotacaoException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ClienteCotacaoDolar {

    private final RestClient restClientCotacaoDolar;

    public RespostaCotacaoDolar buscarCotacaoDolar() {
        Map<String, CotacaoAwesomeApi> resposta;
        try {
            resposta = restClientCotacaoDolar.get()
                    .uri("/json/last/USD-BRL")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientException ex) {
            throw new FalhaConsultaCotacaoException("Falha ao consultar cotacao do dolar na API externa", ex);
        }

        CotacaoAwesomeApi cotacao = resposta == null ? null : resposta.values().stream().findFirst().orElse(null);
        if (cotacao == null) {
            throw new FalhaConsultaCotacaoException("Resposta vazia ou invalida da API de cotacao");
        }

        return new RespostaCotacaoDolar(
                "USD/BRL",
                new BigDecimal(cotacao.bid()),
                new BigDecimal(cotacao.ask()),
                Instant.now());
    }

    private record CotacaoAwesomeApi(String code, String codein, String bid, String ask) {
    }
}
