package com.bancointer.investrenda.service;

import com.bancointer.investrenda.domain.Conta;
import com.bancointer.investrenda.domain.Produto;
import com.bancointer.investrenda.domain.enums.TipoProduto;
import com.bancointer.investrenda.dto.RequisicaoAplicacao;
import com.bancointer.investrenda.dto.RespostaAplicacao;
import com.bancointer.investrenda.domain.enums.StatusAplicacao;
import com.bancointer.investrenda.exception.NegocioException;
import com.bancointer.investrenda.domain.Aplicacao;
import com.bancointer.investrenda.repository.AplicacaoRepository;
import com.bancointer.investrenda.repository.ContaRepository;
import com.bancointer.investrenda.repository.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AplicacaoServiceTest {

    @Mock
    private AplicacaoRepository aplicacaoRepository;
    @Mock
    private ContaRepository contaRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private AplicacaoService aplicacaoService;

    private Conta conta;
    private Produto produto;

    private void setUp() {
        conta = Conta.builder().id(UUID.randomUUID()).cpf("12345678900")
                .nomeTitular("Cliente Teste").saldo(new BigDecimal("1000.00")).build();
        produto = Produto.builder().id(UUID.randomUUID()).nome("CDB Teste").tipo(TipoProduto.CDB)
                .emissor("Banco X").classificacaoEmissor("AA").taxaAnual(new BigDecimal("12.00"))
                .investimentoMinimo(new BigDecimal("100.00")).prazoMeses(12).ativo(true).build();

        ReflectionTestUtils.setField(aplicacaoService, "nomeExchange", "test.exchange");
        ReflectionTestUtils.setField(aplicacaoService, "chaveRoteamento", "test.routing.key");
    }

    @Test
    void devePublicarNaFilaQuandoAplicacaoForValida() {
        setUp();
        RequisicaoAplicacao requisicao = new RequisicaoAplicacao(conta.getCpf(), produto.getId(), new BigDecimal("200.00"));

        when(contaRepository.buscarPorCpf(conta.getCpf())).thenReturn(Optional.of(conta));
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));
        when(aplicacaoRepository.saveAndFlush(any(Aplicacao.class)))
                .thenAnswer(invocation -> {
                    Aplicacao aplicacao = invocation.getArgument(0);
                    aplicacao.setId(UUID.randomUUID());
                    aplicacao.setConta(conta);
                    aplicacao.setProduto(produto);
                    return aplicacao;
                });

        RespostaAplicacao resposta = aplicacaoService.solicitarAplicacao(requisicao);

        assertThat(resposta.status()).isEqualTo(StatusAplicacao.PENDENTE);
        verify(rabbitTemplate).convertAndSend(eq("test.exchange"), eq("test.routing.key"), any(Object.class));
    }

    @Test
    void deveRejeitarQuandoValorAbaixoDoMinimoDoProduto() {
        setUp();
        RequisicaoAplicacao requisicao = new RequisicaoAplicacao(conta.getCpf(), produto.getId(), new BigDecimal("10.00"));

        when(contaRepository.buscarPorCpf(conta.getCpf())).thenReturn(Optional.of(conta));
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));

        assertThatThrownBy(() -> aplicacaoService.solicitarAplicacao(requisicao))
                .isInstanceOf(NegocioException.class);

        verifyNoInteractions(rabbitTemplate);
    }
}
