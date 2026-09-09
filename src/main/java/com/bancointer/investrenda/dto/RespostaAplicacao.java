package com.bancointer.investrenda.dto;

import com.bancointer.investrenda.domain.Aplicacao;
import com.bancointer.investrenda.domain.enums.StatusAplicacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Estado de uma aplicacao. `status` comeca em PENDENTE e evolui de forma "
        + "assincrona (via fila) para CONCLUIDA ou FALHOU - use GET /aplicacoes/{id} em polling "
        + "para acompanhar.")
public record RespostaAplicacao(

        @Schema(description = "Identificador da aplicacao.", example = "38d604dc-6797-466c-a236-206bf9b738f6")
        UUID id,

        @Schema(description = "Identificador da conta que aplicou.", example = "0ff4300d-ed80-4f91-a610-81c48e3aef41")
        UUID contaId,

        @Schema(description = "CPF da conta que aplicou (resolvido via join, sem precisar de outra chamada).",
                example = "11144477735")
        String contaCpf,

        @Schema(description = "Identificador do produto aplicado.", example = "1adbc6db-79aa-4a0e-b352-0113d4267383")
        UUID produtoId,

        @Schema(description = "Nome do produto aplicado (resolvido via join).", example = "CDB Banco Inter 130% CDI")
        String produtoNome,

        @Schema(example = "300.00")
        BigDecimal valor,

        @Schema(description = "PENDENTE (aguardando processamento assincrono) -> CONCLUIDA ou FALHOU.")
        StatusAplicacao status,

        @Schema(description = "Motivo da falha, preenchido so quando `status` = FALHOU (ex.: saldo insuficiente).",
                nullable = true)
        String motivoFalha,

        @Schema(description = "Taxa vigente do produto no momento do processamento, preenchida so quando "
                + "`status` != PENDENTE.", example = "14.2000", nullable = true)
        BigDecimal taxaAplicada,

        @Schema(description = "Data de vencimento calculada no processamento, preenchida so quando "
                + "`status` != PENDENTE.", example = "2027-09-08", nullable = true)
        LocalDate dataVencimento,

        Instant criadoEm
) {

    public static RespostaAplicacao from(Aplicacao aplicacao) {
        return new RespostaAplicacao(
                aplicacao.getId(),
                aplicacao.getConta().getId(),
                aplicacao.getConta().getCpf(),
                aplicacao.getProduto().getId(),
                aplicacao.getProduto().getNome(),
                aplicacao.getValor(),
                aplicacao.getStatus(),
                aplicacao.getMotivoFalha(),
                aplicacao.getTaxaAplicada(),
                aplicacao.getDataVencimento(),
                aplicacao.getCriadoEm()
        );
    }
}
