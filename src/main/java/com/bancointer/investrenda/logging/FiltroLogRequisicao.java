package com.bancointer.investrenda.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
public class FiltroLogRequisicao extends OncePerRequestFilter {

    private static final String CABECALHO_REQUEST_ID = "X-Request-Id";
    private static final Pattern CPF_NA_URL = Pattern.compile("\\b\\d{11}\\b");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestIdOuNovo(request);
        MDC.put("requestId", requestId);
        response.setHeader(CABECALHO_REQUEST_ID, requestId);
        long inicio = System.currentTimeMillis();

        try {
            chain.doFilter(request, response);
        } finally {
            log.info("Requisicao processada",
                    kv("httpMetodo", request.getMethod()),
                    kv("httpCaminho", CPF_NA_URL.matcher(request.getRequestURI()).replaceAll("***")),
                    kv("httpStatus", response.getStatus()),
                    kv("duracaoMs", System.currentTimeMillis() - inicio));
            MDC.remove("requestId");
        }
    }

    private String requestIdOuNovo(HttpServletRequest request) {
        String recebido = request.getHeader(CABECALHO_REQUEST_ID);
        return recebido != null && !recebido.isBlank() ? recebido : UUID.randomUUID().toString();
    }
}
