package com.bancointer.investrenda.messaging;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

public record MensagemAplicacaoSolicitada(
        UUID aplicacaoId,
        UUID contaId,
        UUID produtoId,
        BigDecimal valor
) implements Serializable {
}
