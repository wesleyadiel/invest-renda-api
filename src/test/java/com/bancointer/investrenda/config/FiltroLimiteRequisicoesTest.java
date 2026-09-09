package com.bancointer.investrenda.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FiltroLimiteRequisicoesTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PropriedadesLimiteRequisicoes propriedadesComCapacidade(int capacidade) {
        PropriedadesLimiteRequisicoes propriedades = new PropriedadesLimiteRequisicoes();
        propriedades.setHabilitado(true);
        propriedades.setCapacidade(capacidade);
        propriedades.setTokensReposicao(capacidade);
        propriedades.setDuracaoReposicaoSegundos(60);
        return propriedades;
    }

    @BeforeEach
    void setUp() {
        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        lenient().when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    }

    @Test
    void devePermitirRequisicoesDentroDaCapacidade() throws Exception {
        FiltroLimiteRequisicoes filtro = new FiltroLimiteRequisicoes(propriedadesComCapacidade(2), objectMapper);

        filtro.doFilter(request, response, chain);
        filtro.doFilter(request, response, chain);

        verify(chain, times(2)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void deveRetornar429ComCorpoNoEnvelopeQuandoCapacidadeExcedida() throws Exception {
        FiltroLimiteRequisicoes filtro = new FiltroLimiteRequisicoes(propriedadesComCapacidade(1), objectMapper);
        StringWriter corpo = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(corpo));

        filtro.doFilter(request, response, chain); // consome o unico token
        filtro.doFilter(request, response, chain); // deveria ser bloqueado

        verify(chain, times(1)).doFilter(request, response);
        verify(response).setStatus(429);
        verify(response).setHeader(eq("Retry-After"), any());
        assertThat(corpo.toString())
                .contains("\"sucesso\":false")
                .contains("Limite de requisicoes excedido");
    }

    @Test
    void deveRastrearClientesDiferentesSeparadamente() throws Exception {
        FiltroLimiteRequisicoes filtro = new FiltroLimiteRequisicoes(propriedadesComCapacidade(1), objectMapper);

        HttpServletRequest outroCliente = mock(HttpServletRequest.class);
        lenient().when(outroCliente.getRemoteAddr()).thenReturn("10.0.0.9");

        filtro.doFilter(request, response, chain);       // esgota o bucket de 127.0.0.1
        filtro.doFilter(outroCliente, response, chain);   // outro cliente, bucket proprio

        verify(chain, times(2)).doFilter(any(), eq(response));
        verify(response, never()).setStatus(429);
    }

    @Test
    void deveDesviarCompletamenteQuandoDesabilitado() throws Exception {
        PropriedadesLimiteRequisicoes propriedades = propriedadesComCapacidade(1);
        propriedades.setHabilitado(false);
        FiltroLimiteRequisicoes filtro = new FiltroLimiteRequisicoes(propriedades, objectMapper);

        filtro.doFilter(request, response, chain);
        filtro.doFilter(request, response, chain);
        filtro.doFilter(request, response, chain);

        verify(chain, times(3)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void devePreferirForwardedForEmVezDeRemoteAddr() throws Exception {
        FiltroLimiteRequisicoes filtro = new FiltroLimiteRequisicoes(propriedadesComCapacidade(1), objectMapper);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        HttpServletRequest mesmoClienteEncaminhado = mock(HttpServletRequest.class);
        lenient().when(mesmoClienteEncaminhado.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5");
        lenient().when(mesmoClienteEncaminhado.getRemoteAddr()).thenReturn("10.0.0.99"); // proxy diferente, mesmo cliente real

        filtro.doFilter(request, response, chain);
        filtro.doFilter(mesmoClienteEncaminhado, response, chain); // mesmo IP de origem -> mesmo bucket, deve bloquear

        verify(chain, times(1)).doFilter(any(), eq(response));
        verify(response).setStatus(429);
    }
}
