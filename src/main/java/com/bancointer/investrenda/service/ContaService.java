package com.bancointer.investrenda.service;

import com.bancointer.investrenda.domain.Conta;
import com.bancointer.investrenda.dto.RequisicaoConta;
import com.bancointer.investrenda.dto.RequisicaoDeposito;
import com.bancointer.investrenda.dto.RespostaConta;
import com.bancointer.investrenda.exception.NaoEncontradoException;
import com.bancointer.investrenda.exception.NegocioException;
import com.bancointer.investrenda.exception.RecursoDuplicadoException;
import com.bancointer.investrenda.logging.MascaraDados;
import com.bancointer.investrenda.repository.AplicacaoRepository;
import com.bancointer.investrenda.repository.ContaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContaService {

    private final ContaRepository contaRepository;
    private final AplicacaoRepository aplicacaoRepository;

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

        conta = contaRepository.saveAndFlush(conta);
        log.info("Conta criada",
                kv("contaId", conta.getId()), kv("cpf", MascaraDados.mascararCpf(conta.getCpf())), kv("saldoInicial", conta.getSaldo()));
        return RespostaConta.from(conta);
    }

    // Soma ao saldo atual, nunca substitui - unico jeito de repor saldo em
    // uma conta ja existente (ex: apos zerar num teste). Usa o mesmo lock
    // pessimista que ConsumidorRequisicaoAplicacao usa para debitar, para
    // nao perder incremento se um debito concorrente estiver em andamento.
    @Transactional
    public RespostaConta depositar(String cpf, RequisicaoDeposito requisicao) {
        Conta conta = contaRepository.buscarPorCpfParaAtualizacao(cpf)
                .orElseThrow(() -> new NaoEncontradoException("Conta nao encontrada para o cpf: " + cpf));

        conta.depositar(requisicao.valor());
        conta = contaRepository.saveAndFlush(conta);

        log.info("Deposito realizado",
                kv("contaId", conta.getId()), kv("cpf", MascaraDados.mascararCpf(cpf)), kv("valor", requisicao.valor()));
        return RespostaConta.from(conta);
    }

    @Transactional(readOnly = true)
    public RespostaConta buscarPorCpf(String cpf) {
        Conta conta = contaRepository.buscarPorCpf(cpf)
                .orElseThrow(() -> new NaoEncontradoException("Conta nao encontrada para o cpf: " + cpf));
        return RespostaConta.from(conta);
    }

    @Transactional
    public void excluir(String cpf) {
        Conta conta = contaRepository.buscarPorCpf(cpf)
                .orElseThrow(() -> new NaoEncontradoException("Conta nao encontrada para o cpf: " + cpf));

        if (aplicacaoRepository.existePorContaId(conta.getId())) {
            throw new NegocioException("Nao e possivel excluir uma conta com aplicacoes associadas");
        }

        contaRepository.delete(conta);
        log.info("Conta excluida", kv("contaId", conta.getId()), kv("cpf", MascaraDados.mascararCpf(cpf)));
    }

    @Transactional(readOnly = true)
    public List<RespostaConta> listarTodas() {
        return contaRepository.findAll().stream()
                .map(RespostaConta::from)
                .toList();
    }
}
