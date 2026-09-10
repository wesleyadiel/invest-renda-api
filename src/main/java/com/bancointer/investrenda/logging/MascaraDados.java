package com.bancointer.investrenda.logging;

public final class MascaraDados {

    private MascaraDados() {
    }

    // Mantem os 3 primeiros e os 2 ultimos digitos visiveis (padrao usado por
    // bancos para exibir CPF parcialmente) - suficiente para conferencia
    // visual em log sem expor o documento completo (LGPD, dado pessoal).
    public static String mascararCpf(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            return "***";
        }
        return cpf.substring(0, 3) + ".***.***-" + cpf.substring(9);
    }
}
