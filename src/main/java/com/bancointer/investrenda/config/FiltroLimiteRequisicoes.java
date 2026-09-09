package com.bancointer.investrenda.config;

import com.bancointer.investrenda.dto.RespostaApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Um bucket de tokens por cliente (IP), compartilhado entre todos os
 * endpoints cobertos por este filtro (ver registro em
 * {@link ConfiguracaoLimiteRequisicoes}). Em memoria - cada instancia da
 * API tem seu proprio conjunto de buckets, o que e adequado para o deploy
 * atual (uma instancia), mas nao seria suficiente para varias instancias
 * atras de um load balancer sem migrar para um backend compartilhado
 * (ex: bucket4j-redis).
 */
public class FiltroLimiteRequisicoes extends OncePerRequestFilter {

    private final PropriedadesLimiteRequisicoes propriedades;
    private final ObjectMapper objectMapper;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public FiltroLimiteRequisicoes(PropriedadesLimiteRequisicoes propriedades, ObjectMapper objectMapper) {
        this.propriedades = propriedades;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!propriedades.isHabilitado()) {
            chain.doFilter(request, response);
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(resolverChaveCliente(request), chave -> novoBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(probe.getRemainingTokens(), 0)));

        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long segundosParaEsperar = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(segundosParaEsperar));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                RespostaApi.criarErro("Limite de requisicoes excedido. Tente novamente em " + segundosParaEsperar + "s.")));
    }

    private Bucket novoBucket() {
        Bandwidth limite = Bandwidth.classic(propriedades.getCapacidade(),
                Refill.greedy(propriedades.getTokensReposicao(), Duration.ofSeconds(propriedades.getDuracaoReposicaoSegundos())));
        return Bucket.builder().addLimit(limite).build();
    }

    private String resolverChaveCliente(HttpServletRequest request) {
        String encaminhadoPor = request.getHeader("X-Forwarded-For");
        if (encaminhadoPor != null && !encaminhadoPor.isBlank()) {
            return encaminhadoPor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
