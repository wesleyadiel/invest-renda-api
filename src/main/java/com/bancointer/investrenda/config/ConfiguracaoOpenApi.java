package com.bancointer.investrenda.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Metadados globais do Swagger/OpenAPI (titulo, descricao, contato, ordem
 * das tags) - sem isso o springdoc gera uma documentacao so com os
 * endpoints "nus", sem nenhum contexto de negocio.
 *
 * A ordem da lista de tags aqui e a ordem em que os grupos aparecem no
 * Swagger UI (segue o fluxo natural de uso da API: produto -> conta ->
 * aplicacao -> observabilidade -> integracao externa). As anotacoes
 * {@code @Tag(name = "...")} nos controllers so associam cada operacao ao
 * grupo certo por nome - a descricao de cada grupo mora so aqui, para nao
 * duplicar texto em dois lugares.
 */
@Configuration
public class ConfiguracaoOpenApi {

    @Bean
    public OpenAPI investRendaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("invest-renda-api")
                        .version("v1")
                        .description("""
                                Microsservico REST de investimentos em renda fixa (CDB, LCI, LCA): \
                                cadastro de produtos, contas por CPF e aplicacoes com debito de saldo \
                                processado de forma **assincrona** via fila (RabbitMQ).

                                ## Envelope de resposta
                                Toda resposta - sucesso ou erro - vem no mesmo formato:
                                ```json
                                { "sucesso": true, "dados": { }, "mensagem": "...", "erros": [] }
                                ```
                                `dados` e sempre `null` em respostas de erro; `erros` só tem mais de um \
                                item em erro de validacao (um item por campo invalido) - nos demais \
                                casos a causa unica fica em `mensagem`. Os exemplos de cada endpoint \
                                abaixo mostram o payload de `dados` isoladamente, ja "desembrulhado", \
                                para focar no formato de cada recurso.

                                ## Rate limit
                                Todo endpoint de negocio (`/produtos`, `/contas`, `/aplicacoes`, \
                                `/operacoes`, `/integracoes`) e limitado a 120 requisicoes/minuto por \
                                IP (`429 Too Many Requests` ao exceder, header `Retry-After`).

                                ## Autenticacao
                                Nao ha - e uma API de validacao/avaliacao, sem dado sensivel de producao.
                                """)
                        .contact(new Contact()
                                .name("wesleyadiel")
                                .url("https://github.com/wesleyadiel/invest-renda-api"))
                        .license(new License().name("Uso privado")))
                .tags(List.of(
                        new Tag().name("Produtos")
                                .description("Cadastro de produtos de renda fixa - nome, tipo, emissor, taxa, "
                                        + "valor minimo e prazo. Listagem e detalhe passam por cache Redis."),
                        new Tag().name("Contas")
                                .description("Abertura de conta vinculada a um CPF (com digito verificador "
                                        + "validado) e consulta de saldo."),
                        new Tag().name("Aplicações")
                                .description("Aplicacao de um valor em um produto. A resposta imediata "
                                        + "(202 Accepted) traz a aplicacao em PENDENTE; o debito de saldo "
                                        + "acontece depois, processado de forma assincrona por um worker "
                                        + "que escuta uma fila."),
                        new Tag().name("Observabilidade")
                                .description("Estado ao vivo do RabbitMQ (fila de aplicacoes e dead-letter "
                                        + "queue) e do Redis (cache de produtos) - existe para dar suporte "
                                        + "ao front-end de validacao, nao faz parte do dominio de negocio."),
                        new Tag().name("Integrações Externas")
                                .description("Exemplo de chamada a uma API externa (cotacao do dolar) "
                                        + "protegida por feature toggle, retry com backoff exponencial e "
                                        + "circuit breaker (Resilience4j) - inclui rotas para simular falha "
                                        + "e observar o comportamento sem depender da API externa estar fora do ar.")
                ));
    }
}
