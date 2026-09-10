package com.bancointer.investrenda.controller;

import com.bancointer.investrenda.domain.enums.StatusAplicacao;
import com.bancointer.investrenda.dto.RequisicaoAplicacao;
import com.bancointer.investrenda.dto.RespostaAplicacao;
import com.bancointer.investrenda.exception.NegocioException;
import com.bancointer.investrenda.exception.NaoEncontradoException;
import com.bancointer.investrenda.service.AplicacaoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AplicacaoController.class)
class AplicacaoControllerTest {

    private static final String CPF_VALIDO = "11144477735";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AplicacaoService aplicacaoService;

    private RespostaAplicacao respostaPendente(UUID id) {
        return new RespostaAplicacao(id, UUID.randomUUID(), CPF_VALIDO, UUID.randomUUID(), "CDB Teste",
                new BigDecimal("300.00"), StatusAplicacao.PENDENTE, null, null, null, Instant.now());
    }

    @Test
    void deveAceitarRequisicaoDeAplicacao() throws Exception {
        UUID id = UUID.randomUUID();
        RequisicaoAplicacao requisicao = new RequisicaoAplicacao(CPF_VALIDO, UUID.randomUUID(), new BigDecimal("300.00"));
        when(aplicacaoService.solicitarAplicacao(any(RequisicaoAplicacao.class))).thenReturn(respostaPendente(id));

        mockMvc.perform(post("/aplicacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicao)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/aplicacoes/" + id))
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.dados.status").value("PENDENTE"));
    }

    @Test
    void deveRejeitarRequisicaoComCpfInvalidoSemChamarService() throws Exception {
        RequisicaoAplicacao requisicao = new RequisicaoAplicacao("11111111111", UUID.randomUUID(), new BigDecimal("300.00"));

        mockMvc.perform(post("/aplicacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicao)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(aplicacaoService);
    }

    @Test
    void deveRetornarNaoProcessavelQuandoRegraDeNegocioFalha() throws Exception {
        RequisicaoAplicacao requisicao = new RequisicaoAplicacao(CPF_VALIDO, UUID.randomUUID(), new BigDecimal("1.00"));
        when(aplicacaoService.solicitarAplicacao(any(RequisicaoAplicacao.class)))
                .thenThrow(new NegocioException("Valor abaixo do minimo exigido pelo produto: 100.00"));

        mockMvc.perform(post("/aplicacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicao)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.sucesso").value(false))
                .andExpect(jsonPath("$.mensagem").value("Valor abaixo do minimo exigido pelo produto: 100.00"));
    }

    @Test
    void deveBuscarAplicacaoPorId() throws Exception {
        UUID id = UUID.randomUUID();
        when(aplicacaoService.buscarPorId(id)).thenReturn(respostaPendente(id));

        mockMvc.perform(get("/aplicacoes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.dados.id").value(id.toString()));
    }

    @Test
    void deveRetornarBadRequestQuandoIdNaoEUuidValido() throws Exception {
        mockMvc.perform(get("/aplicacoes/{id}", "nao-e-um-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.sucesso").value(false));

        verifyNoInteractions(aplicacaoService);
    }

    @Test
    void deveRetornarNaoEncontradoQuandoAplicacaoNaoExiste() throws Exception {
        UUID id = UUID.randomUUID();
        when(aplicacaoService.buscarPorId(id)).thenThrow(new NaoEncontradoException("Aplicacao nao encontrada: " + id));

        mockMvc.perform(get("/aplicacoes/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveListarAplicacoes() throws Exception {
        UUID id = UUID.randomUUID();
        when(aplicacaoService.listarTodas()).thenReturn(List.of(respostaPendente(id)));

        mockMvc.perform(get("/aplicacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.dados[0].contaCpf").value(CPF_VALIDO));
    }
}
