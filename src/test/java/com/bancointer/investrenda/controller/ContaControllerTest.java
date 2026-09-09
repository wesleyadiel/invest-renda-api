package com.bancointer.investrenda.controller;

import com.bancointer.investrenda.dto.RequisicaoConta;
import com.bancointer.investrenda.dto.RespostaConta;
import com.bancointer.investrenda.exception.RecursoDuplicadoException;
import com.bancointer.investrenda.exception.NaoEncontradoException;
import com.bancointer.investrenda.service.ContaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
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

@WebMvcTest(ContaController.class)
class ContaControllerTest {

    private static final String CPF_VALIDO = "11144477735";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ContaService contaService;

    @Test
    void deveCriarConta() throws Exception {
        RequisicaoConta requisicao = new RequisicaoConta(CPF_VALIDO, "Cliente Teste", new BigDecimal("100.00"));
        RespostaConta resposta = new RespostaConta(UUID.randomUUID(), CPF_VALIDO, "Cliente Teste", new BigDecimal("100.00"));
        when(contaService.criar(any(RequisicaoConta.class))).thenReturn(resposta);

        mockMvc.perform(post("/contas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicao)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/contas/" + CPF_VALIDO))
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.dados.cpf").value(CPF_VALIDO))
                .andExpect(jsonPath("$.mensagem").value("Conta criada com sucesso"));
    }

    @Test
    void deveRejeitarCpfMatematicamenteInvalidoSemChamarService() throws Exception {
        RequisicaoConta requisicao = new RequisicaoConta("12345678900", "Cliente Teste", null);

        mockMvc.perform(post("/contas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicao)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.sucesso").value(false))
                .andExpect(jsonPath("$.erros[0]").value(org.hamcrest.Matchers.containsString("CPF")));

        verifyNoInteractions(contaService);
    }

    @Test
    void deveRetornarConflitoQuandoCpfJaTemConta() throws Exception {
        RequisicaoConta requisicao = new RequisicaoConta(CPF_VALIDO, "Cliente Teste", null);
        when(contaService.criar(any(RequisicaoConta.class)))
                .thenThrow(new RecursoDuplicadoException("Ja existe uma conta para o cpf informado"));

        mockMvc.perform(post("/contas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicao)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.sucesso").value(false))
                .andExpect(jsonPath("$.mensagem").value("Ja existe uma conta para o cpf informado"));
    }

    @Test
    void deveBuscarContaPorCpf() throws Exception {
        RespostaConta resposta = new RespostaConta(UUID.randomUUID(), CPF_VALIDO, "Cliente Teste", BigDecimal.TEN);
        when(contaService.buscarPorCpf(CPF_VALIDO)).thenReturn(resposta);

        mockMvc.perform(get("/contas/{cpf}", CPF_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.dados.saldo").value(10));
    }

    @Test
    void deveRetornarNaoEncontradoQuandoContaNaoExiste() throws Exception {
        when(contaService.buscarPorCpf(CPF_VALIDO))
                .thenThrow(new NaoEncontradoException("Conta nao encontrada para o cpf: " + CPF_VALIDO));

        mockMvc.perform(get("/contas/{cpf}", CPF_VALIDO))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveListarContas() throws Exception {
        RespostaConta resposta = new RespostaConta(UUID.randomUUID(), CPF_VALIDO, "Cliente Teste", BigDecimal.ONE);
        when(contaService.listarTodas()).thenReturn(List.of(resposta));

        mockMvc.perform(get("/contas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.dados[0].cpf").value(CPF_VALIDO));
    }
}
