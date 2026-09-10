package com.bancointer.investrenda.repository;

import com.bancointer.investrenda.domain.Conta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

/**
 * Os nomes dos metodos aqui nao podem ser derivados automaticamente pelo
 * Spring Data (a derivacao so reconhece palavras-chave em ingles como
 * "findBy"/"existsBy") - por isso cada um tem sua propria @Query explicita,
 * mesmo os mais simples.
 */
public interface ContaRepository extends JpaRepository<Conta, UUID> {

    @Query("select c from Conta c where c.cpf = :cpf")
    Optional<Conta> buscarPorCpf(String cpf);

    @Query("select case when count(c) > 0 then true else false end from Conta c where c.cpf = :cpf")
    boolean existePorCpf(String cpf);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conta c where c.id = :id")
    Optional<Conta> buscarPorIdParaAtualizacao(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conta c where c.cpf = :cpf")
    Optional<Conta> buscarPorCpfParaAtualizacao(String cpf);
}
