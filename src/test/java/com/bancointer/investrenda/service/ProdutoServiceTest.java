package com.bancointer.investrenda.service;

import com.bancointer.investrenda.domain.Aplicacao;
import com.bancointer.investrenda.domain.Conta;
import com.bancointer.investrenda.domain.Produto;
import com.bancointer.investrenda.domain.enums.StatusAplicacao;
import com.bancointer.investrenda.domain.enums.TipoProduto;
import com.bancointer.investrenda.dto.RequisicaoProduto;
import com.bancointer.investrenda.dto.RespostaDetalheProduto;
import com.bancointer.investrenda.dto.RespostaRelatorioProduto;
import com.bancointer.investrenda.exception.NaoEncontradoException;
import com.bancointer.investrenda.exception.NegocioException;
import com.bancointer.investrenda.repository.AplicacaoRepository;
import com.bancointer.investrenda.repository.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private AplicacaoRepository aplicacaoRepository;

    @InjectMocks
    private ProdutoService produtoService;

    @Test
    void deveCriarProdutoComAtivoTrueComoDefault() {
        RequisicaoProduto requisicao = new RequisicaoProduto(
                "CDB Banco X", TipoProduto.CDB, "Banco X", "AA",
                new BigDecimal("12.50"), new BigDecimal("500.00"), 12, null);

        when(produtoRepository.saveAndFlush(any(Produto.class)))
                .thenAnswer(invocation -> {
                    Produto produto = invocation.getArgument(0);
                    produto.setId(UUID.randomUUID());
                    return produto;
                });

        RespostaDetalheProduto resposta = produtoService.criar(requisicao);

        assertThat(resposta.ativo()).isTrue();
        assertThat(resposta.nome()).isEqualTo("CDB Banco X");
    }

    @Test
    void deveLancarNaoEncontradoQuandoProdutoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(produtoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.buscarDetalhe(id))
                .isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void deveExcluirProdutoSemAplicacoesAssociadas() {
        UUID id = UUID.randomUUID();
        Produto produto = Produto.builder().id(id).nome("CDB Teste").build();

        when(produtoRepository.findById(id)).thenReturn(Optional.of(produto));
        when(aplicacaoRepository.existePorProdutoId(id)).thenReturn(false);

        produtoService.excluir(id);

        verify(produtoRepository).delete(produto);
    }

    @Test
    void deveLancarNaoEncontradoAoExcluirProdutoInexistente() {
        UUID id = UUID.randomUUID();
        when(produtoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.excluir(id))
                .isInstanceOf(NaoEncontradoException.class);

        verify(produtoRepository, never()).delete(any(Produto.class));
    }

    @Test
    void deveLancarNegocioAoExcluirProdutoComAplicacoesAssociadas() {
        UUID id = UUID.randomUUID();
        Produto produto = Produto.builder().id(id).nome("CDB Teste").build();

        when(produtoRepository.findById(id)).thenReturn(Optional.of(produto));
        when(aplicacaoRepository.existePorProdutoId(id)).thenReturn(true);

        assertThatThrownBy(() -> produtoService.excluir(id))
                .isInstanceOf(NegocioException.class);

        verify(produtoRepository, never()).delete(any(Produto.class));
    }

    private Produto produtoTeste(UUID id) {
        return Produto.builder()
                .id(id).nome("CDB Teste").tipo(TipoProduto.CDB).emissor("Banco X")
                .classificacaoEmissor("AA").taxaAnual(new BigDecimal("12.50"))
                .investimentoMinimo(new BigDecimal("100.00")).prazoMeses(12).ativo(true)
                .build();
    }

    private Aplicacao aplicacaoTeste(UUID id, Produto produto) {
        Conta conta = Conta.builder().id(UUID.randomUUID()).cpf("11144477735")
                .nomeTitular("Cliente Teste").saldo(new BigDecimal("1000.00")).build();
        return Aplicacao.builder()
                .id(id).conta(conta).produto(produto).valor(new BigDecimal("300.00"))
                .status(StatusAplicacao.CONCLUIDA).build();
    }

    @Test
    void deveGerarRelatorioComAplicacoesDoProduto() {
        UUID produtoId = UUID.randomUUID();
        Produto produto = produtoTeste(produtoId);
        Aplicacao aplicacao = aplicacaoTeste(UUID.randomUUID(), produto);

        when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produto));
        when(aplicacaoRepository.buscarPorProdutoIdOrdenadasPorDataCriacaoDesc(produtoId))
                .thenReturn(List.of(aplicacao));

        RespostaRelatorioProduto relatorio = produtoService.gerarRelatorioAplicacoes(produtoId);

        assertThat(relatorio.produto().id()).isEqualTo(produtoId);
        assertThat(relatorio.totalAplicacoes()).isEqualTo(1);
        assertThat(relatorio.aplicacoes()).hasSize(1);
        assertThat(relatorio.aplicacoes().get(0).produtoId()).isEqualTo(produtoId);
    }

    @Test
    void deveGerarRelatorioComListaVaziaQuandoProdutoNaoTemAplicacoes() {
        UUID produtoId = UUID.randomUUID();
        Produto produto = produtoTeste(produtoId);

        when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produto));
        when(aplicacaoRepository.buscarPorProdutoIdOrdenadasPorDataCriacaoDesc(produtoId))
                .thenReturn(List.of());

        RespostaRelatorioProduto relatorio = produtoService.gerarRelatorioAplicacoes(produtoId);

        assertThat(relatorio.totalAplicacoes()).isZero();
        assertThat(relatorio.aplicacoes()).isEmpty();
    }

    @Test
    void deveLancarNaoEncontradoAoGerarRelatorioDeProdutoInexistente() {
        UUID produtoId = UUID.randomUUID();
        when(produtoRepository.findById(produtoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.gerarRelatorioAplicacoes(produtoId))
                .isInstanceOf(NaoEncontradoException.class);

        verify(aplicacaoRepository, never()).buscarPorProdutoIdOrdenadasPorDataCriacaoDesc(any(UUID.class));
    }

    @Test
    void deveLancarInconsistenciaQuandoAplicacaoRetornadaPertenceAOutroProduto() {
        UUID produtoId = UUID.randomUUID();
        Produto produtoBuscado = produtoTeste(produtoId);
        Produto outroProduto = produtoTeste(UUID.randomUUID());
        Aplicacao aplicacaoDeOutroProduto = aplicacaoTeste(UUID.randomUUID(), outroProduto);

        when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoBuscado));
        when(aplicacaoRepository.buscarPorProdutoIdOrdenadasPorDataCriacaoDesc(produtoId))
                .thenReturn(List.of(aplicacaoDeOutroProduto));

        assertThatThrownBy(() -> produtoService.gerarRelatorioAplicacoes(produtoId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(produtoId.toString())
                .hasMessageContaining(outroProduto.getId().toString());
    }

    @Test
    void deveLancarInconsistenciaQuandoAplicacaoRetornadaNaoTemProdutoAssociado() {
        UUID produtoId = UUID.randomUUID();
        Produto produtoBuscado = produtoTeste(produtoId);
        Aplicacao aplicacaoSemProduto = aplicacaoTeste(UUID.randomUUID(), null);

        when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoBuscado));
        when(aplicacaoRepository.buscarPorProdutoIdOrdenadasPorDataCriacaoDesc(produtoId))
                .thenReturn(List.of(aplicacaoSemProduto));

        assertThatThrownBy(() -> produtoService.gerarRelatorioAplicacoes(produtoId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(produtoId.toString());
    }
}
