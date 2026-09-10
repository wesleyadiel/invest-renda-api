package com.bancointer.investrenda.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Appender registrado no logback-spring.xml ao lado do console - so guarda
// as ultimas linhas em memoria (reseta a cada restart, sem persistencia de
// proposito) e reenvia para quem estiver assinando via SSE
// (GET /operacoes/logs/stream). Estatico porque o Logback sobe antes do
// contexto Spring existir - nao da para injetar isso como bean comum.
public class AppenderLogAoVivo extends AppenderBase<ILoggingEvent> {

    private static final int LIMITE_LINHAS = 300;

    private static final Deque<String> BUFFER = new ArrayDeque<>(LIMITE_LINHAS);
    private static final List<SseEmitter> ASSINANTES = new CopyOnWriteArrayList<>();

    private Encoder<ILoggingEvent> encoder;

    public void setEncoder(Encoder<ILoggingEvent> encoder) {
        this.encoder = encoder;
    }

    @Override
    public void start() {
        if (encoder != null && !encoder.isStarted()) {
            encoder.start();
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        String linha = new String(encoder.encode(event), StandardCharsets.UTF_8).strip();

        synchronized (BUFFER) {
            BUFFER.addLast(linha);
            if (BUFFER.size() > LIMITE_LINHAS) {
                BUFFER.pollFirst();
            }
        }

        for (SseEmitter assinante : ASSINANTES) {
            enviar(assinante, linha);
        }
    }

    public static SseEmitter assinar() {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> ASSINANTES.remove(emitter));
        emitter.onTimeout(() -> ASSINANTES.remove(emitter));
        emitter.onError(ex -> ASSINANTES.remove(emitter));

        synchronized (BUFFER) {
            BUFFER.forEach(linha -> enviar(emitter, linha));
        }
        ASSINANTES.add(emitter);
        return emitter;
    }

    // Best-effort: um assinante com a conexao ja morta (aba fechada, cliente
    // sumiu) pode falhar de varias formas diferentes dependendo de onde a
    // desconexao foi percebida na pilha do Tomcat/Spring - nao so
    // IOException. Falha aqui nunca deveria propagar (e nem faz sentido
    // reportar erro por um assinante que simplesmente nao esta mais la).
    private static void enviar(SseEmitter assinante, String linha) {
        try {
            assinante.send(SseEmitter.event().data(linha, MediaType.APPLICATION_JSON));
        } catch (Exception ex) {
            ASSINANTES.remove(assinante);
        }
    }
}
