package com.bancointer.investrenda.controller;

import com.bancointer.investrenda.dto.RequisicaoConta;
import com.bancointer.investrenda.dto.RequisicaoDeposito;
import com.bancointer.investrenda.dto.RespostaApi;
import com.bancointer.investrenda.dto.RespostaConta;
import com.bancointer.investrenda.service.ContaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@Tag(name = "Contas")
@RestController
@RequestMapping("/contas")
@RequiredArgsConstructor
public class ContaController {

    private final ContaService contaService;

    @Operation(summary = "Abrir conta",
            description = "Cria uma conta vinculada a um CPF unico, com saldo inicial opcional.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conta criada.",
                    content = @Content(schema = @Schema(implementation = RespostaConta.class))),
            @ApiResponse(responseCode = "400", description = "Payload invalido (CPF com formato errado ou "
                    + "matematicamente invalido, nome curto demais, saldo negativo).", content = @Content(examples = @ExampleObject(value = """
                    {"sucesso": false, "dados": null, "mensagem": "Erro de validacao",
                     "erros": ["cpf: CPF invalido"]}"""))),
            @ApiResponse(responseCode = "409", description = "Ja existe uma conta para o CPF informado.", content = @Content(examples = @ExampleObject(value = """
                    {"sucesso": false, "dados": null, "mensagem": "Ja existe uma conta para o cpf informado", "erros": []}""")))
    })
    @PostMapping
    public ResponseEntity<RespostaApi<RespostaConta>> criar(@Valid @RequestBody RequisicaoConta requisicao) {
        RespostaConta criada = contaService.criar(requisicao);
        return ResponseEntity.created(URI.create("/contas/" + criada.cpf()))
                .body(RespostaApi.criarSucesso(criada, "Conta criada com sucesso"));
    }

    @Operation(summary = "Consultar conta e saldo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conta encontrada.",
                    content = @Content(schema = @Schema(implementation = RespostaConta.class))),
            @ApiResponse(responseCode = "404", description = "Nao existe conta para esse CPF.")
    })
    @GetMapping("/{cpf}")
    public ResponseEntity<RespostaApi<RespostaConta>> buscarPorCpf(
            @Parameter(description = "CPF do titular (11 digitos).", example = "11144477735") @PathVariable String cpf) {
        RespostaConta conta = contaService.buscarPorCpf(cpf);
        return ResponseEntity.ok(RespostaApi.criarSucesso(conta, "Conta encontrada"));
    }

    @Operation(summary = "Depositar na conta",
            description = "Soma o valor informado ao saldo atual - nao substitui. Util para repor saldo de "
                    + "uma conta zerada (por exemplo, apos aplicacoes de teste terem debitado tudo) sem "
                    + "precisar excluir e recriar a conta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deposito realizado, saldo atualizado.",
                    content = @Content(schema = @Schema(implementation = RespostaConta.class))),
            @ApiResponse(responseCode = "400", description = "Valor ausente, zero ou negativo.", content = @Content(examples = @ExampleObject(value = """
                    {"sucesso": false, "dados": null, "mensagem": "Erro de validacao",
                     "erros": ["valor: valor deve ser maior que zero"]}"""))),
            @ApiResponse(responseCode = "404", description = "Nao existe conta para esse CPF.")
    })
    @PostMapping("/{cpf}/depositar")
    public ResponseEntity<RespostaApi<RespostaConta>> depositar(
            @Parameter(description = "CPF do titular (11 digitos).", example = "11144477735") @PathVariable String cpf,
            @Valid @RequestBody RequisicaoDeposito requisicao) {
        RespostaConta conta = contaService.depositar(cpf, requisicao);
        return ResponseEntity.ok(RespostaApi.criarSucesso(conta, "Deposito realizado com sucesso"));
    }

    @Operation(summary = "Listar contas",
            description = "Lista todas as contas cadastradas. Sem paginacao - alimenta a tela de "
                    + "validacao/administracao, nao e um endpoint de alto volume.")
    @ApiResponse(responseCode = "200", description = "Lista de contas (pode vir vazia).",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = RespostaConta.class))))
    @GetMapping
    public ResponseEntity<RespostaApi<List<RespostaConta>>> listar() {
        List<RespostaConta> contas = contaService.listarTodas();
        return ResponseEntity.ok(RespostaApi.criarSucesso(contas, "Contas listadas com sucesso"));
    }

    @Operation(summary = "Excluir conta",
            description = "So e permitido se nao houver nenhuma aplicacao associada a essa conta - o "
                    + "historico de aplicacoes nao pode ficar com uma referencia quebrada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conta excluida.", content = @Content(examples = @ExampleObject(value = """
                    {"sucesso": true, "dados": null, "mensagem": "Conta excluida com sucesso", "erros": []}"""))),
            @ApiResponse(responseCode = "404", description = "Nao existe conta para esse CPF."),
            @ApiResponse(responseCode = "422", description = "Existe pelo menos uma aplicacao associada a "
                    + "esta conta - exclusao bloqueada.", content = @Content(examples = @ExampleObject(value = """
                    {"sucesso": false, "dados": null,
                     "mensagem": "Nao e possivel excluir uma conta com aplicacoes associadas", "erros": []}""")))
    })
    @DeleteMapping("/{cpf}")
    public ResponseEntity<RespostaApi<Void>> excluir(
            @Parameter(description = "CPF do titular (11 digitos).", example = "11144477735") @PathVariable String cpf) {
        contaService.excluir(cpf);
        return ResponseEntity.ok(RespostaApi.criarSucesso(null, "Conta excluida com sucesso"));
    }
}
