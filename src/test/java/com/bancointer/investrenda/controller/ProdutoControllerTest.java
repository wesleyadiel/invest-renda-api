package com.bancointer.investrenda.controller;

import com.bancointer.investrenda.domain.enums.TipoProduto;
import com.bancointer.investrenda.dto.RespostaDetalheProduto;
import com.bancointer.investrenda.dto.RequisicaoProduto;
import com.bancointer.investrenda.dto.RespostaResumoProduto;
import com.bancointer.investrenda.exception.NaoEncontradoException;
import com.bancointer.investrenda.exception.NegocioException;
import com.bancointer.investrenda.service.ProdutoService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de camada web (MockMvc, sem subir servidor real). Cobrem: request
 * bem formado chegando ate o service certo, validacao de payload invalido
 * (Bean Validation real, nao mockada) e o ManipuladorGlobalExcecao
 * traduzindo excecoes de service para os status HTTP certos.
 */
@WebMvcTest(ProdutoController.class)
class ProdutoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProdutoService produtoService;

    private RequisicaoProduto requisicaoValida() {
        return new RequisicaoProduto("CDB Teste", TipoProduto.CDB, "Banco X", "AA",
                new BigDecimal("12.50"), new BigDecimal("100.00"), 12, true);
    }

    private RespostaDetalheProduto respostaDetalhe(UUID id) {
        return new RespostaDetalheProduto(id, "CDB Teste", TipoProduto.CDB, "Banco X", "AA",
                new BigDecimal("12.50"), new BigDecimal("100.00"), 12, true, Instant.now(), Instant.now());
    }

    @Test
    void deveCriarProduto() throws Exception {
        UUID id = UUID.randomUUID();
        when(produtoService.criar(any(RequisicaoProduto.class))).thenReturn(respostaDetalhe(id));

        mockMvc.perform(post("/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicaoValida())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/produtos/" + id))
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.dados.nome").value("CDB Teste"))
                .andExpect(jsonPath("$.mensagem").value("Produto criado com sucesso"))
                .andExpect(jsonPath("$.erros").isEmpty());
    }

    @Test
    void deveRejeitarProdutoInvalidoSemChamarService() throws Exception {
        RequisicaoProduto invalida = new RequisicaoProduto("", null, "", "", null, null, null, null);

        mockMvc.perform(post("/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalida)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.sucesso").value(false))
                .andExpect(jsonPath("$.dados").doesNotExist())
                .andExpect(jsonPath("$.mensagem").value("Erro de validacao"))
                .andExpect(jsonPath("$.erros").isNotEmpty());

        verifyNoInteractions(produtoService);
    }

    @Test
    void deveAtualizarProduto() throws Exception {
        UUID id = UUID.randomUUID();
        when(produtoService.atualizar(eq(id), any(RequisicaoProduto.class))).thenReturn(respostaDetalhe(id));

        mockMvc.perform(put("/produtos/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicaoValida())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.dados.id").value(id.toString()));
    }

    @Test
    void deveRetornarNaoEncontradoAoAtualizarProdutoInexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(produtoService.atualizar(eq(id), any(RequisicaoProduto.class)))
                .thenThrow(new NaoEncontradoException("Produto nao encontrado: " + id));

        mockMvc.perform(put("/produtos/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicaoValida())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.sucesso").value(false))
                .andExpect(jsonPath("$.mensagem").value("Produto nao encontrado: " + id));
    }

    @Test
    void deveListarProdutos() throws Exception {
        RespostaResumoProduto resumo = new RespostaResumoProduto(UUID.randomUUID(), "CDB Teste",
                TipoProduto.CDB, new BigDecimal("12.50"), new BigDecimal("100.00"), 12);
        when(produtoService.listarResumo()).thenReturn(List.of(resumo));

        mockMvc.perform(get("/produtos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.dados[0].nome").value("CDB Teste"));
    }

    @Test
    void deveBuscarDetalheDoProduto() throws Exception {
        UUID id = UUID.randomUUID();
        when(produtoService.buscarDetalhe(id)).thenReturn(respostaDetalhe(id));

        mockMvc.perform(get("/produtos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.dados.id").value(id.toString()));
    }

    @Test
    void deveRetornarNaoEncontradoQuandoDetalheDoProdutoNaoExiste() throws Exception {
        UUID id = UUID.randomUUID();
        when(produtoService.buscarDetalhe(id)).thenThrow(new NaoEncontradoException("Produto nao encontrado: " + id));

        mockMvc.perform(get("/produtos/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveExcluirProduto() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(produtoService).excluir(id);

        mockMvc.perform(delete("/produtos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.dados").doesNotExist())
                .andExpect(jsonPath("$.mensagem").value("Produto excluido com sucesso"));
    }

    @Test
    void deveRetornarNaoEncontradoAoExcluirProdutoInexistente() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new NaoEncontradoException("Produto nao encontrado: " + id)).when(produtoService).excluir(id);

        mockMvc.perform(delete("/produtos/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.sucesso").value(false));
    }

    @Test
    void deveRetornarErroDeNegocioAoExcluirProdutoComAplicacoesAssociadas() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new NegocioException("Nao e possivel excluir um produto com aplicacoes associadas"))
                .when(produtoService).excluir(id);

        mockMvc.perform(delete("/produtos/{id}", id))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.sucesso").value(false))
                .andExpect(jsonPath("$.mensagem").value("Nao e possivel excluir um produto com aplicacoes associadas"));
    }

    @Test
    void deveRetornarErroInternoNoEnvelopeParaExcecaoInesperada() throws Exception {
        UUID id = UUID.randomUUID();
        when(produtoService.buscarDetalhe(id)).thenThrow(new IllegalStateException("falha inesperada de infraestrutura"));

        mockMvc.perform(get("/produtos/{id}", id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.sucesso").value(false))
                .andExpect(jsonPath("$.dados").doesNotExist())
                .andExpect(jsonPath("$.mensagem").value("falha inesperada de infraestrutura"))
                .andExpect(jsonPath("$.erros").isEmpty());
    }
}
