package com.bancointer.investrenda.service;

import com.bancointer.investrenda.domain.Produto;
import com.bancointer.investrenda.domain.enums.TipoProduto;
import com.bancointer.investrenda.dto.RequisicaoProduto;
import com.bancointer.investrenda.dto.RespostaDetalheProduto;
import com.bancointer.investrenda.exception.NaoEncontradoException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock
    private ProdutoRepository produtoRepository;

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
}
