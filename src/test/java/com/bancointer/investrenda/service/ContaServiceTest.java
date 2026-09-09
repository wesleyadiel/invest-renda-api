package com.bancointer.investrenda.service;

import com.bancointer.investrenda.domain.Conta;
import com.bancointer.investrenda.dto.RequisicaoConta;
import com.bancointer.investrenda.dto.RespostaConta;
import com.bancointer.investrenda.exception.NaoEncontradoException;
import com.bancointer.investrenda.exception.NegocioException;
import com.bancointer.investrenda.exception.RecursoDuplicadoException;
import com.bancointer.investrenda.repository.AplicacaoRepository;
import com.bancointer.investrenda.repository.ContaRepository;
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
class ContaServiceTest {

    @Mock
    private ContaRepository contaRepository;

    @Mock
    private AplicacaoRepository aplicacaoRepository;

    @InjectMocks
    private ContaService contaService;

    @Test
    void deveCriarContaComSaldoZeroQuandoSaldoInicialForNulo() {
        RequisicaoConta requisicao = new RequisicaoConta("11144477735", "Cliente Teste", null);

        when(contaRepository.existePorCpf(requisicao.cpf())).thenReturn(false);
        when(contaRepository.saveAndFlush(any(Conta.class)))
                .thenAnswer(invocation -> {
                    Conta conta = invocation.getArgument(0);
                    conta.setId(UUID.randomUUID());
                    return conta;
                });

        RespostaConta resposta = contaService.criar(requisicao);

        assertThat(resposta.saldo()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resposta.cpf()).isEqualTo("11144477735");
    }

    @Test
    void deveCriarContaComSaldoInicialInformado() {
        RequisicaoConta requisicao = new RequisicaoConta("11144477735", "Cliente Teste", new BigDecimal("500.00"));

        when(contaRepository.existePorCpf(requisicao.cpf())).thenReturn(false);
        when(contaRepository.saveAndFlush(any(Conta.class)))
                .thenAnswer(invocation -> {
                    Conta conta = invocation.getArgument(0);
                    conta.setId(UUID.randomUUID());
                    return conta;
                });

        RespostaConta resposta = contaService.criar(requisicao);

        assertThat(resposta.saldo()).isEqualByComparingTo("500.00");
    }

    @Test
    void deveLancarRecursoDuplicadoQuandoCpfJaTemConta() {
        RequisicaoConta requisicao = new RequisicaoConta("11144477735", "Cliente Teste", null);

        when(contaRepository.existePorCpf(requisicao.cpf())).thenReturn(true);

        assertThatThrownBy(() -> contaService.criar(requisicao))
                .isInstanceOf(RecursoDuplicadoException.class);

        verify(contaRepository, never()).saveAndFlush(any(Conta.class));
    }

    @Test
    void deveRetornarContaQuandoEncontradaPorCpf() {
        Conta conta = Conta.builder()
                .id(UUID.randomUUID()).cpf("11144477735").nomeTitular("Cliente Teste")
                .saldo(new BigDecimal("100.00")).build();

        when(contaRepository.buscarPorCpf("11144477735")).thenReturn(Optional.of(conta));

        RespostaConta resposta = contaService.buscarPorCpf("11144477735");

        assertThat(resposta.cpf()).isEqualTo("11144477735");
        assertThat(resposta.saldo()).isEqualByComparingTo("100.00");
    }

    @Test
    void deveLancarNaoEncontradoQuandoCpfNaoTemConta() {
        when(contaRepository.buscarPorCpf("11144477735")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contaService.buscarPorCpf("11144477735"))
                .isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void deveListarTodasAsContas() {
        Conta primeira = Conta.builder().id(UUID.randomUUID()).cpf("11144477735")
                .nomeTitular("Cliente A").saldo(BigDecimal.TEN).build();
        Conta segunda = Conta.builder().id(UUID.randomUUID()).cpf("52998224725")
                .nomeTitular("Cliente B").saldo(BigDecimal.ONE).build();

        when(contaRepository.findAll()).thenReturn(List.of(primeira, segunda));

        List<RespostaConta> respostas = contaService.listarTodas();

        assertThat(respostas).hasSize(2)
                .extracting(RespostaConta::cpf)
                .containsExactly("11144477735", "52998224725");
    }

    @Test
    void deveExcluirContaSemAplicacoesAssociadas() {
        UUID id = UUID.randomUUID();
        Conta conta = Conta.builder().id(id).cpf("11144477735").nomeTitular("Cliente Teste")
                .saldo(BigDecimal.ZERO).build();

        when(contaRepository.buscarPorCpf("11144477735")).thenReturn(Optional.of(conta));
        when(aplicacaoRepository.existePorContaId(id)).thenReturn(false);

        contaService.excluir("11144477735");

        verify(contaRepository).delete(conta);
    }

    @Test
    void deveLancarNaoEncontradoAoExcluirContaInexistente() {
        when(contaRepository.buscarPorCpf("11144477735")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contaService.excluir("11144477735"))
                .isInstanceOf(NaoEncontradoException.class);

        verify(contaRepository, never()).delete(any(Conta.class));
    }

    @Test
    void deveLancarNegocioAoExcluirContaComAplicacoesAssociadas() {
        UUID id = UUID.randomUUID();
        Conta conta = Conta.builder().id(id).cpf("11144477735").nomeTitular("Cliente Teste")
                .saldo(BigDecimal.ZERO).build();

        when(contaRepository.buscarPorCpf("11144477735")).thenReturn(Optional.of(conta));
        when(aplicacaoRepository.existePorContaId(id)).thenReturn(true);

        assertThatThrownBy(() -> contaService.excluir("11144477735"))
                .isInstanceOf(NegocioException.class);

        verify(contaRepository, never()).delete(any(Conta.class));
    }
}
