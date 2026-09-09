package com.bancointer.investrenda.service;

import com.bancointer.investrenda.domain.Conta;
import com.bancointer.investrenda.domain.Aplicacao;
import com.bancointer.investrenda.domain.Produto;
import com.bancointer.investrenda.domain.enums.StatusAplicacao;
import com.bancointer.investrenda.domain.enums.TipoProduto;
import com.bancointer.investrenda.messaging.MensagemAplicacaoSolicitada;
import com.bancointer.investrenda.repository.AplicacaoRepository;
import com.bancointer.investrenda.repository.ContaRepository;
import com.bancointer.investrenda.repository.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumidorRequisicaoAplicacaoTest {

    @Mock
    private AplicacaoRepository aplicacaoRepository;
    @Mock
    private ContaRepository contaRepository;
    @Mock
    private ProdutoRepository produtoRepository;

    @InjectMocks
    private ConsumidorRequisicaoAplicacao consumidor;

    @Test
    void deveConcluirAplicacaoEDebitarSaldoQuandoHouverSaldoSuficiente() {
        Conta conta = Conta.builder().id(UUID.randomUUID()).cpf("12345678900")
                .nomeTitular("Cliente").saldo(new BigDecimal("1000.00")).build();
        Produto produto = Produto.builder().id(UUID.randomUUID()).nome("CDB").tipo(TipoProduto.CDB)
                .emissor("Banco X").classificacaoEmissor("AA").taxaAnual(new BigDecimal("12.00"))
                .investimentoMinimo(new BigDecimal("100.00")).prazoMeses(6).ativo(true).build();
        Aplicacao aplicacao = Aplicacao.builder().id(UUID.randomUUID()).conta(conta).produto(produto)
                .valor(new BigDecimal("300.00")).status(StatusAplicacao.PENDENTE).build();

        MensagemAplicacaoSolicitada mensagem = new MensagemAplicacaoSolicitada(
                aplicacao.getId(), conta.getId(), produto.getId(), aplicacao.getValor());

        when(aplicacaoRepository.findById(aplicacao.getId())).thenReturn(Optional.of(aplicacao));
        when(contaRepository.buscarPorIdParaAtualizacao(conta.getId())).thenReturn(Optional.of(conta));
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));

        consumidor.processar(mensagem);

        assertThat(aplicacao.getStatus()).isEqualTo(StatusAplicacao.CONCLUIDA);
        assertThat(conta.getSaldo()).isEqualByComparingTo("700.00");
        assertThat(aplicacao.getDataVencimento()).isNotNull();
    }

    @Test
    void deveFalharAplicacaoQuandoSaldoInsuficiente() {
        Conta conta = Conta.builder().id(UUID.randomUUID()).cpf("12345678900")
                .nomeTitular("Cliente").saldo(new BigDecimal("50.00")).build();
        Produto produto = Produto.builder().id(UUID.randomUUID()).nome("CDB").tipo(TipoProduto.CDB)
                .emissor("Banco X").classificacaoEmissor("AA").taxaAnual(new BigDecimal("12.00"))
                .investimentoMinimo(new BigDecimal("100.00")).prazoMeses(6).ativo(true).build();
        Aplicacao aplicacao = Aplicacao.builder().id(UUID.randomUUID()).conta(conta).produto(produto)
                .valor(new BigDecimal("300.00")).status(StatusAplicacao.PENDENTE).build();

        MensagemAplicacaoSolicitada mensagem = new MensagemAplicacaoSolicitada(
                aplicacao.getId(), conta.getId(), produto.getId(), aplicacao.getValor());

        when(aplicacaoRepository.findById(aplicacao.getId())).thenReturn(Optional.of(aplicacao));
        when(contaRepository.buscarPorIdParaAtualizacao(conta.getId())).thenReturn(Optional.of(conta));
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));

        consumidor.processar(mensagem);

        assertThat(aplicacao.getStatus()).isEqualTo(StatusAplicacao.FALHOU);
        assertThat(aplicacao.getMotivoFalha()).isNotBlank();
        assertThat(conta.getSaldo()).isEqualByComparingTo("50.00");
        verify(contaRepository, never()).save(any(Conta.class));
    }

    @Test
    void deveIgnorarMensagemQuandoAplicacaoJaProcessada() {
        Aplicacao aplicacao = Aplicacao.builder().id(UUID.randomUUID())
                .status(StatusAplicacao.CONCLUIDA).valor(new BigDecimal("100.00")).build();
        MensagemAplicacaoSolicitada mensagem = new MensagemAplicacaoSolicitada(
                aplicacao.getId(), UUID.randomUUID(), UUID.randomUUID(), aplicacao.getValor());

        when(aplicacaoRepository.findById(aplicacao.getId())).thenReturn(Optional.of(aplicacao));

        consumidor.processar(mensagem);

        verify(contaRepository, never()).buscarPorIdParaAtualizacao(any());
    }
}
