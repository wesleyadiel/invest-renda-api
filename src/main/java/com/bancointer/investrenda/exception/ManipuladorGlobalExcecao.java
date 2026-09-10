package com.bancointer.investrenda.exception;

import com.bancointer.investrenda.dto.RespostaApi;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@RestControllerAdvice
public class ManipuladorGlobalExcecao {

    @ExceptionHandler(NaoEncontradoException.class)
    public ResponseEntity<RespostaApi<Object>> tratarNaoEncontrado(NaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(RespostaApi.criarErro(ex.getMessage()));
    }

    @ExceptionHandler(RecursoDuplicadoException.class)
    public ResponseEntity<RespostaApi<Object>> tratarRecursoDuplicado(RecursoDuplicadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(RespostaApi.criarErro(ex.getMessage()));
    }

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<RespostaApi<Object>> tratarNegocio(NegocioException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(RespostaApi.criarErro(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespostaApi<Object>> tratarValidacao(MethodArgumentNotValidException ex) {
        List<String> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(RespostaApi.criarErro("Erro de validacao", erros));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RespostaApi<Object>> tratarViolacaoConstraint(ConstraintViolationException ex) {
        return ResponseEntity.badRequest()
                .body(RespostaApi.criarErro(ex.getMessage()));
    }

    // Sem estes dois, "id" invalido num @PathVariable UUID ou num campo UUID
    // do corpo JSON cairia no catch-all de Exception abaixo e voltaria 500 -
    // o handler generico "engole" os 400 que o Spring devolveria sozinho por
    // padrao para esses dois casos, entao precisam de tratamento explicito
    // aqui para o cliente continuar recebendo 400 (erro dele) e nao 500
    // (que sugeriria bug do servidor).
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RespostaApi<Object>> tratarTipoArgumentoInvalido(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(RespostaApi.criarErro(ex.getName() + ": valor invalido '" + ex.getValue() + "'"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RespostaApi<Object>> tratarCorpoInvalido(HttpMessageNotReadableException ex) {
        Throwable causa = ex.getMostSpecificCause();
        return ResponseEntity.badRequest()
                .body(RespostaApi.criarErro("Corpo da requisicao invalido: " + (causa != null ? causa.getMessage() : ex.getMessage())));
    }

    @ExceptionHandler(IntegracaoExternaException.class)
    public ResponseEntity<RespostaApi<Object>> tratarIntegracaoExterna(IntegracaoExternaException ex) {
        log.warn("Integracao externa indisponivel", kv("motivo", ex.getMessage()));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(RespostaApi.criarErro(ex.getMessage()));
    }

    // Cliente desconectou no meio de uma resposta em stream (aba fechada,
    // rede caiu) - Tomcat/Spring reportam isso como IOException ("Broken
    // pipe"/"Connection reset") mesmo em conexoes assincronas como o SSE de
    // /operacoes/logs/stream. Nao e bug da aplicacao nem acionavel, e nao
    // ha mais ninguem do outro lado do socket para receber resposta -
    // retornar void evita que o proprio Spring tente escrever (e falhar
    // escrevendo) uma resposta de erro nessa conexao ja morta.
    @ExceptionHandler(IOException.class)
    public void tratarConexaoInterrompida(IOException ex) {
        log.debug("Conexao interrompida pelo cliente", kv("motivo", ex.getMessage()));
    }

    // Exposta ao cliente de proposito (nao escondida atras de um "erro
    // interno" generico) - decisao deliberada para facilitar debug do
    // consumidor da API; o log em ERROR com stack trace cobre a
    // investigacao do lado do servidor.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespostaApi<Object>> tratarNaoTratada(Exception ex) {
        log.error("Excecao nao tratada", kv("tipoExcecao", ex.getClass().getSimpleName()), ex);
        String mensagem = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RespostaApi.criarErro(mensagem));
    }
}
