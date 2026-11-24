package com.bancointer.investrenda.domain;

import com.bancointer.investrenda.domain.enums.TipoProduto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "produto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Produto {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoProduto tipo;

    @Column(nullable = false, length = 120)
    private String emissor;

    @Column(name = "classificacao_emissor", nullable = false, length = 10)
    private String classificacaoEmissor;

    @Column(name = "taxa_anual", nullable = false, precision = 7, scale = 4)
    private BigDecimal taxaAnual;

    @Column(name = "investimento_minimo", nullable = false, precision = 18, scale = 2)
    private BigDecimal investimentoMinimo;

    @Column(name = "prazo_meses", nullable = false)
    private Integer prazoMeses;

    @Column(nullable = false)
    private boolean ativo;

    // mappedBy porque Aplicacao ja e dona do relacionamento (@ManyToOne +
    // @JoinColumn "produto_id"); sem mappedBy o Hibernate tratava os dois
    // lados como donos da mesma coluna, gerando um UPDATE extra e
    // desnecessario a cada Aplicacao salva so para "confirmar" a FK.
    @OneToMany(mappedBy = "produto", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Aplicacao> aplicacoes = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}
