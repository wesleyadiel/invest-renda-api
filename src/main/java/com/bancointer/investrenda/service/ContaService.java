package com.bancointer.investrenda.service;

import com.bancointer.investrenda.domain.Conta;
import com.bancointer.investrenda.dto.RequisicaoConta;
import com.bancointer.investrenda.dto.RespostaConta;
import com.bancointer.investrenda.exception.NaoEncontradoException;
import com.bancointer.investrenda.exception.RecursoDuplicadoException;
import com.bancointer.investrenda.repository.ContaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContaService {

    private final ContaRepository contaRepository;

    @Transactional
    public RespostaConta criar(RequisicaoConta requisicao) {
        if (contaRepository.existePorCpf(requisicao.cpf())) {
            throw new RecursoDuplicadoException("Ja existe uma conta para o cpf informado");
        }

        Conta conta = Conta.builder()
                .cpf(requisicao.cpf())
                .nomeTitular(requisicao.nomeTitular())
                .saldo(requisicao.saldoInicial() != null ? requisicao.saldoInicial() : BigDecimal.ZERO)
                .build();

        return RespostaConta.from(contaRepository.saveAndFlush(conta));
    }

    @Transactional(readOnly = true)
    public RespostaConta buscarPorCpf(String cpf) {
        Conta conta = contaRepository.buscarPorCpf(cpf)
                .orElseThrow(() -> new NaoEncontradoException("Conta nao encontrada para o cpf: " + cpf));
        return RespostaConta.from(conta);
    }

    @Transactional(readOnly = true)
    public List<RespostaConta> listarTodas() {
        return contaRepository.findAll().stream()
                .map(RespostaConta::from)
                .toList();
    }
}
