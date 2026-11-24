package com.bancointer.investrenda.repository;

import com.bancointer.investrenda.domain.Aplicacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AplicacaoRepository extends JpaRepository<Aplicacao, UUID> {

    @Query("select a from Aplicacao a join fetch a.conta join fetch a.produto order by a.criadoEm desc")
    List<Aplicacao> buscarTodasOrdenadasPorDataCriacaoDesc();

    @Query("select case when count(a) > 0 then true else false end from Aplicacao a where a.conta.id = :contaId")
    boolean existePorContaId(UUID contaId);

    @Query("select case when count(a) > 0 then true else false end from Aplicacao a where a.produto.id = :produtoId")
    boolean existePorProdutoId(UUID produtoId);

    @Query("select a from Aplicacao a join fetch a.conta join fetch a.produto "
            + "where a.produto.id = :produtoId order by a.criadoEm desc")
    List<Aplicacao> buscarPorProdutoIdOrdenadasPorDataCriacaoDesc(UUID produtoId);
}
