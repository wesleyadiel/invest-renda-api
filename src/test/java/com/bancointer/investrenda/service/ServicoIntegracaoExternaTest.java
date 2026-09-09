package com.bancointer.investrenda.service;

import com.bancointer.investrenda.config.PropriedadesIntegracaoExterna;
import com.bancointer.investrenda.dto.RespostaCotacaoDolar;
import com.bancointer.investrenda.dto.StatusIntegracaoExterna;
import com.bancointer.investrenda.exception.FalhaConsultaCotacaoException;
import com.bancointer.investrenda.exception.IntegracaoDesabilitadaException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Testa so a logica de negocio do metodo anotado (feature toggle, toggle de
 * simulacao de falha, delegacao ao cliente) - chamado direto, sem passar
 * pelo proxy AOP do Spring, entao retry/circuit breaker/fallback do
 * Resilience4j nao entram em acao aqui (por isso os testes esperam a
 * excecao "crua", nao o resultado do fallback). Esse comportamento de
 * resiliencia e validado manualmente via as rotas de simulacao, nao por
 * teste automatizado.
 */
@ExtendWith(MockitoExtension.class)
class ServicoIntegracaoExternaTest {

    @Mock
    private ClienteCotacaoDolar clienteCotacaoDolar;

    private EstadoIntegracaoExterna estado;
    private ServicoIntegracaoExterna servico;

    @BeforeEach
    void setUp() {
        PropriedadesIntegracaoExterna propriedades = new PropriedadesIntegracaoExterna();
        propriedades.setHabilitado(true);
        estado = new EstadoIntegracaoExterna(propriedades);
        servico = new ServicoIntegracaoExterna(clienteCotacaoDolar, estado, CircuitBreakerRegistry.ofDefaults());
    }

    @Test
    void deveDelegarAoClienteQuandoHabilitadaESemSimulacao() {
        RespostaCotacaoDolar esperado = new RespostaCotacaoDolar(
                "USD/BRL", new BigDecimal("5.10"), new BigDecimal("5.11"), Instant.now());
        when(clienteCotacaoDolar.buscarCotacaoDolar()).thenReturn(esperado);

        RespostaCotacaoDolar resposta = servico.consultarCotacaoDolar();

        assertThat(resposta).isEqualTo(esperado);
    }

    @Test
    void deveRejeitarQuandoIntegracaoDesabilitada() {
        estado.setHabilitado(false);

        assertThatThrownBy(() -> servico.consultarCotacaoDolar())
                .isInstanceOf(IntegracaoDesabilitadaException.class);

        verifyNoInteractions(clienteCotacaoDolar);
    }

    @Test
    void deveForcarFalhaQuandoSimulacaoAtiva() {
        estado.setSimularFalha(true);

        assertThatThrownBy(() -> servico.consultarCotacaoDolar())
                .isInstanceOf(FalhaConsultaCotacaoException.class);

        verifyNoInteractions(clienteCotacaoDolar);
    }

    @Test
    void statusDeveRefletirTogglesEEstadoDoCircuito() {
        estado.setSimularFalha(true);

        StatusIntegracaoExterna status = servico.status();

        assertThat(status.habilitado()).isTrue();
        assertThat(status.simulandoFalha()).isTrue();
        assertThat(status.estadoCircuito()).isEqualTo("CLOSED");
    }

    @Test
    void habilitarESimularFalhaDevemAtualizarOEstadoCompartilhado() {
        servico.habilitar(false);
        servico.simularFalha(true);

        assertThat(estado.isHabilitado()).isFalse();
        assertThat(estado.isSimularFalha()).isTrue();
    }
}
