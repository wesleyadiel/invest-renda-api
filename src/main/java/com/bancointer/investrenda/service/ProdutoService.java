package com.bancointer.investrenda.service;

import com.bancointer.investrenda.config.ConfiguracaoCacheRedis;
import com.bancointer.investrenda.domain.Produto;
import com.bancointer.investrenda.dto.RequisicaoProduto;
import com.bancointer.investrenda.dto.RespostaDetalheProduto;
import com.bancointer.investrenda.dto.RespostaResumoProduto;
import com.bancointer.investrenda.exception.NaoEncontradoException;
import com.bancointer.investrenda.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepository;

    @Transactional
    @CacheEvict(cacheNames = ConfiguracaoCacheRedis.CACHE_PRODUTOS, allEntries = true)
    public RespostaDetalheProduto criar(RequisicaoProduto requisicao) {
        Produto produto = Produto.builder()
                .nome(requisicao.nome())
                .tipo(requisicao.tipo())
                .emissor(requisicao.emissor())
                .classificacaoEmissor(requisicao.classificacaoEmissor())
                .taxaAnual(requisicao.taxaAnual())
                .investimentoMinimo(requisicao.investimentoMinimo())
                .prazoMeses(requisicao.prazoMeses())
                .ativo(requisicao.ativo() == null || requisicao.ativo())
                .build();

        return RespostaDetalheProduto.from(produtoRepository.saveAndFlush(produto));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = ConfiguracaoCacheRedis.CACHE_PRODUTOS, allEntries = true),
            @CacheEvict(cacheNames = ConfiguracaoCacheRedis.CACHE_PRODUTO, key = "#id")
    })
    public RespostaDetalheProduto atualizar(UUID id, RequisicaoProduto requisicao) {
        Produto produto = buscarEntidadePorId(id);

        produto.setNome(requisicao.nome());
        produto.setTipo(requisicao.tipo());
        produto.setEmissor(requisicao.emissor());
        produto.setClassificacaoEmissor(requisicao.classificacaoEmissor());
        produto.setTaxaAnual(requisicao.taxaAnual());
        produto.setInvestimentoMinimo(requisicao.investimentoMinimo());
        produto.setPrazoMeses(requisicao.prazoMeses());
        if (requisicao.ativo() != null) {
            produto.setAtivo(requisicao.ativo());
        }

        return RespostaDetalheProduto.from(produtoRepository.saveAndFlush(produto));
    }

    @Cacheable(cacheNames = ConfiguracaoCacheRedis.CACHE_PRODUTOS)
    @Transactional(readOnly = true)
    public List<RespostaResumoProduto> listarResumo() {
        return produtoRepository.findAll().stream()
                .map(RespostaResumoProduto::from)
                .toList();
    }

    @Cacheable(cacheNames = ConfiguracaoCacheRedis.CACHE_PRODUTO, key = "#id")
    @Transactional(readOnly = true)
    public RespostaDetalheProduto buscarDetalhe(UUID id) {
        return RespostaDetalheProduto.from(buscarEntidadePorId(id));
    }

    protected Produto buscarEntidadePorId(UUID id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new NaoEncontradoException("Produto nao encontrado: " + id));
    }
}
