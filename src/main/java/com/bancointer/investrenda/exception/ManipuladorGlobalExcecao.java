package com.bancointer.investrenda.exception;

import com.bancointer.investrenda.dto.RespostaApi;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Traduz toda excecao (tratada ou nao) para o mesmo envelope de resposta
 * ({@link RespostaApi}) usado nos sucessos - nenhum endpoint devolve o
 * formato de erro padrao do Spring.
 */
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

    @ExceptionHandler(IntegracaoExternaException.class)
    public ResponseEntity<RespostaApi<Object>> tratarIntegracaoExterna(IntegracaoExternaException ex) {
        log.warn("Integracao externa indisponivel: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(RespostaApi.criarErro(ex.getMessage()));
    }

    /**
     * Qualquer excecao nao tratada explicitamente acima (bug, falha de
     * infraestrutura, etc.) cai aqui - continua no formato padrao da API, e
     * a mensagem real da excecao e exposta, nao escondida atras de um
     * "erro interno" generico. Loga em nivel ERROR com a stack trace para
     * investigacao (a mensagem devolvida ao cliente por si so raramente e
     * suficiente para diagnosticar a causa).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespostaApi<Object>> tratarNaoTratada(Exception ex) {
        log.error("Excecao nao tratada", ex);
        String mensagem = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RespostaApi.criarErro(mensagem));
    }
}
