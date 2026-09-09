package com.bancointer.investrenda.repository;

import com.bancointer.investrenda.domain.Aplicacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AplicacaoRepository extends JpaRepository<Aplicacao, UUID> {

    @Query("select a from Aplicacao a join fetch a.conta join fetch a.produto order by a.criadoEm desc")
    List<Aplicacao> buscarTodasOrdenadasPorDataCriacaoDesc();
}
