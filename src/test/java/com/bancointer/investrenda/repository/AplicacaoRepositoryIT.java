package com.bancointer.investrenda.repository;

import com.bancointer.investrenda.domain.Conta;
import com.bancointer.investrenda.domain.Aplicacao;
import com.bancointer.investrenda.domain.Produto;
import com.bancointer.investrenda.domain.enums.StatusAplicacao;
import com.bancointer.investrenda.domain.enums.TipoProduto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integracao (nao unitario): sobe um Postgres real via
 * Testcontainers, aplica as migrations Flyway de verdade e roda as duas
 * queries JPQL customizadas do projeto - o join fetch de
 * {@link AplicacaoRepository#buscarTodasOrdenadasPorDataCriacaoDesc()} e o
 * lock pessimista de {@link ContaRepository#buscarPorIdParaAtualizacao}. Um
 * repositorio mockado (como nos testes de service) nunca executa SQL de
 * verdade, entao nao pega um erro de sintaxe ou de mapeamento nessas duas
 * queries - so um teste contra banco real pega isso.
 *
 * Nao roda em "mvn test" (o nome termina em "IT", nao "Test" - convencao do
 * Failsafe) - roda em "mvn verify", e precisa do Docker disponivel.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AplicacaoRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AplicacaoRepository aplicacaoRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Test
    void deveListarAplicacoesOrdenadasPorDataCriacaoDescComContaEProdutoJaBuscados() throws InterruptedException {
        Conta conta = contaRepository.saveAndFlush(Conta.builder()
                .cpf("11144477735").nomeTitular("Cliente Teste").saldo(new BigDecimal("1000.00")).build());

        Produto produto = produtoRepository.saveAndFlush(Produto.builder()
                .nome("CDB Teste").tipo(TipoProduto.CDB).emissor("Banco X").classificacaoEmissor("AA")
                .taxaAnual(new BigDecimal("12.00")).investimentoMinimo(new BigDecimal("100.00"))
                .prazoMeses(12).ativo(true).build());

        Aplicacao primeira = aplicacaoRepository.saveAndFlush(Aplicacao.builder()
                .conta(conta).produto(produto).valor(new BigDecimal("100.00"))
                .status(StatusAplicacao.CONCLUIDA).build());

        Thread.sleep(5); // garante criadoEm distinto entre as duas aplicacoes

        Aplicacao segunda = aplicacaoRepository.saveAndFlush(Aplicacao.builder()
                .conta(conta).produto(produto).valor(new BigDecimal("200.00"))
                .status(StatusAplicacao.PENDENTE).build());

        List<Aplicacao> aplicacoes = aplicacaoRepository.buscarTodasOrdenadasPorDataCriacaoDesc();

        assertThat(aplicacoes).hasSize(2);
        assertThat(aplicacoes.get(0).getId()).isEqualTo(segunda.getId());
        assertThat(aplicacoes.get(1).getId()).isEqualTo(primeira.getId());

        // O ponto do join fetch e evitar N+1 e LazyInitializationException ao
        // acessar conta/produto fora do escopo original da query.
        assertThat(aplicacoes.get(0).getConta().getCpf()).isEqualTo("11144477735");
        assertThat(aplicacoes.get(0).getProduto().getNome()).isEqualTo("CDB Teste");
    }

    @Test
    void deveTravarLinhaDaContaParaAtualizacaoSemErro() {
        Conta conta = contaRepository.saveAndFlush(Conta.builder()
                .cpf("52998224725").nomeTitular("Outra Conta").saldo(new BigDecimal("50.00")).build());

        Optional<Conta> travada = contaRepository.buscarPorIdParaAtualizacao(conta.getId());

        assertThat(travada).isPresent();
        assertThat(travada.get().getCpf()).isEqualTo("52998224725");
    }
}
