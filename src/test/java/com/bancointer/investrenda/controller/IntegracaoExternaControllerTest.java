package com.bancointer.investrenda.controller;

import com.bancointer.investrenda.dto.RespostaCotacaoDolar;
import com.bancointer.investrenda.dto.StatusIntegracaoExterna;
import com.bancointer.investrenda.exception.FalhaConsultaCotacaoException;
import com.bancointer.investrenda.exception.IntegracaoDesabilitadaException;
import com.bancointer.investrenda.service.ServicoIntegracaoExterna;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IntegracaoExternaController.class)
class IntegracaoExternaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ServicoIntegracaoExterna servico;

    @Test
    void deveRetornarCotacaoQuandoConsultaBemSucedida() throws Exception {
        RespostaCotacaoDolar cotacao = new RespostaCotacaoDolar(
                "USD/BRL", new BigDecimal("5.10"), new BigDecimal("5.11"), Instant.now());
        when(servico.consultarCotacaoDolar()).thenReturn(cotacao);

        mockMvc.perform(get("/integracoes/cotacao-dolar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.dados.par").value("USD/BRL"));
    }

    @Test
    void deveRetornarServicoIndisponivelQuandoIntegracaoDesabilitada() throws Exception {
        when(servico.consultarCotacaoDolar())
                .thenThrow(new IntegracaoDesabilitadaException("Integracao de cotacao do dolar esta desabilitada"));

        mockMvc.perform(get("/integracoes/cotacao-dolar"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.sucesso").value(false))
                .andExpect(jsonPath("$.mensagem").value("Integracao de cotacao do dolar esta desabilitada"));
    }

    @Test
    void deveRetornarServicoIndisponivelQuandoFalhaNaConsulta() throws Exception {
        when(servico.consultarCotacaoDolar())
                .thenThrow(new FalhaConsultaCotacaoException("Falha simulada via toggle de teste"));

        mockMvc.perform(get("/integracoes/cotacao-dolar"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.sucesso").value(false));
    }

    @Test
    void deveConsultarStatusDaIntegracao() throws Exception {
        when(servico.status()).thenReturn(new StatusIntegracaoExterna(true, false, "CLOSED"));

        mockMvc.perform(get("/integracoes/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.habilitado").value(true))
                .andExpect(jsonPath("$.dados.simulandoFalha").value(false))
                .andExpect(jsonPath("$.dados.estadoCircuito").value("CLOSED"));
    }

    @Test
    void deveHabilitarEDesabilitarAIntegracao() throws Exception {
        when(servico.status()).thenReturn(new StatusIntegracaoExterna(false, false, "CLOSED"));

        mockMvc.perform(put("/integracoes/habilitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ativo", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.habilitado").value(false));

        verify(servico).habilitar(false);
    }

    @Test
    void deveAtivarESimularFalha() throws Exception {
        when(servico.status()).thenReturn(new StatusIntegracaoExterna(true, true, "CLOSED"));

        mockMvc.perform(put("/integracoes/simular-falha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ativo", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.simulandoFalha").value(true));

        verify(servico).simularFalha(true);
    }

    @Test
    void deveRejeitarTogglesSemOCampoAtivo() throws Exception {
        mockMvc.perform(put("/integracoes/habilitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
