package com.bancointer.investrenda.service;

import com.bancointer.investrenda.dto.RespostaCotacaoDolar;
import com.bancointer.investrenda.dto.StatusIntegracaoExterna;
import com.bancointer.investrenda.exception.FalhaConsultaCotacaoException;
import com.bancointer.investrenda.exception.IntegracaoDesabilitadaException;
import com.bancointer.investrenda.exception.IntegracaoIndisponivelException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orquestra a consulta de cotacao do dolar (via {@link ClienteCotacaoDolar})
 * com resiliencia:
 *
 * <ul>
 *   <li>Feature toggle ({@link EstadoIntegracaoExterna#isHabilitado()}) - se
 *       desligado, falha rapido sem contar como falha no circuit breaker
 *       (application.yml, resilience4j.circuitbreaker.instances.cotacaoDolar.
 *       ignore-exceptions).</li>
 *   <li>Toggle de simulacao de falha - forca uma
 *       {@link FalhaConsultaCotacaoException} antes de chamar a API real,
 *       para exercitar o retry (3x, backoff 1s/2s/4s, mesmo padrao usado no
 *       consumer RabbitMQ) e o circuit breaker de forma deterministica.</li>
 *   <li>Retry + circuit breaker (Resilience4j) - o Retry e o decorator mais
 *       externo (padrao da lib): cada tentativa de retry passa pelo circuit
 *       breaker, entao um circuito aberto faz as tentativas restantes
 *       falharem rapido (CallNotPermittedException) em vez de esperar o
 *       backoff inteiro. Por isso o fallback fica no {@code @Retry}, nao no
 *       {@code @CircuitBreaker} - se ficasse no interno, ele resolveria e
 *       mascararia a excecao antes do Retry conseguir tentar de novo.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoIntegracaoExterna {

    private static final String INSTANCIA = "cotacaoDolar";

    private final ClienteCotacaoDolar clienteCotacaoDolar;
    private final EstadoIntegracaoExterna estado;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Retry(name = INSTANCIA, fallbackMethod = "fallbackFalhaOuCircuitoAberto")
    @CircuitBreaker(name = INSTANCIA)
    public RespostaCotacaoDolar consultarCotacaoDolar() {
        if (!estado.isHabilitado()) {
            throw new IntegracaoDesabilitadaException("Integracao de cotacao do dolar esta desabilitada");
        }
        if (estado.isSimularFalha()) {
            throw new FalhaConsultaCotacaoException("Falha simulada via toggle de teste (PUT /integracoes/simular-falha)");
        }
        return clienteCotacaoDolar.buscarCotacaoDolar();
    }

    /**
     * O Resilience4j resolve o fallback pelo tipo da excecao, do mais
     * especifico para o mais generico - essa sobrecarga e escolhida so
     * quando a excecao e exatamente "desabilitada" (ignorada pelo circuit
     * breaker via resilience4j.circuitbreaker.instances.cotacaoDolar.ignore-
     * exceptions no application.yml). Repassa sem mascarar a causa, ja que
     * aqui nao houve nem tentativa de chamada externa.
     */
    private RespostaCotacaoDolar fallbackFalhaOuCircuitoAberto(IntegracaoDesabilitadaException ex) {
        throw ex;
    }

    /**
     * Resolvido para qualquer outra excecao: retries esgotados
     * (FalhaConsultaCotacaoException) ou circuito aberto
     * (CallNotPermittedException). Em ambos os casos, do ponto de vista do
     * cliente da API o resultado e o mesmo: servico indisponivel agora.
     */
    private RespostaCotacaoDolar fallbackFalhaOuCircuitoAberto(Throwable ex) {
        log.warn("Cotacao do dolar indisponivel apos retries/circuit breaker: {}", ex.getMessage());
        throw new IntegracaoIndisponivelException("Servico de cotacao indisponivel no momento, tente novamente em breve");
    }

    public StatusIntegracaoExterna status() {
        io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(INSTANCIA);
        return new StatusIntegracaoExterna(estado.isHabilitado(), estado.isSimularFalha(), circuitBreaker.getState().name());
    }

    public void habilitar(boolean habilitado) {
        estado.setHabilitado(habilitado);
    }

    public void simularFalha(boolean ativo) {
        estado.setSimularFalha(ativo);
    }
}
