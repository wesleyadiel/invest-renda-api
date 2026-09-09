# Cache (Redis)

## Onde é usado

Só para leitura de **produtos** (`ProdutoService`), porque produtos mudam
raramente e são consultados com muito mais frequência do que são
cadastrados/editados — é o caso clássico onde cache compensa.

Duas caches, definidas em `config/ConfiguracaoCacheRedis.java`:

| Cache      | Método             | Chave              | TTL   |
|------------|--------------------| --------------------|-------|
| `produtos` | `listarResumo()`    | `produtos::SimpleKey []` (uma chave só, a lista inteira) | 10 min |
| `produto`  | `buscarDetalhe(id)`    | `produto::{id}`     | 10 min |

Contas e aplicações **não** são cacheadas — mudam a cada operação (saldo,
status), então cache traria mais risco de dado velho do que ganho de
performance.

## Invalidação

`ProdutoService.criar()` invalida `produtos` inteiro (`allEntries = true`
— não dá pra invalidar seletivamente uma lista agregada).

`ProdutoService.atualizar()` invalida `produtos` inteiro **e** a chave
específica `produto::{id}` do produto editado (`@Caching` com dois
`@CacheEvict`).

## Serialização

Por padrão o Spring Cache com Redis serializa em binário Java
(`JdkSerializationRedisSerializer`), o que é frágil (quebra se você mudar a
classe) e não é legível se você inspecionar o Redis manualmente. Configuramos
`GenericJackson2JsonRedisSerializer` para guardar como JSON.

Dois detalhes que renderam bugs reais durante a implementação (documentados
em [`decisions.md`](./decisions.md) com mais contexto):

1. **`JavaTimeModule` precisa ser registrado manualmente** no `ObjectMapper`
   usado pelo serializer — sem isso, qualquer DTO com campo `Instant`
   (`criadoEm`, `atualizadoEm`) quebra a serialização.
2. **`DefaultTyping.EVERYTHING`, não `NON_FINAL`** — os DTOs de resposta
   (`RespostaDetalheProduto`, `RespostaResumoProduto`) são `record`s, que são
   implicitamente `final`. Com `NON_FINAL`, o Jackson não grava a informação
   de tipo desses records ao serializar, mas o `GenericJackson2JsonRedisSerializer`
   sempre espera essa informação ao desserializar — resultado: escreve sem
   problema, mas quebra ao ler de volta do cache.

## Ordem de execução: cache vs. transação

Ver a seção "Cache + Transação" em [`decisions.md`](./decisions.md) — resumo
rápido: `InvestRendaApiApplication` declara
`@EnableCaching(order = 0)` e `@EnableTransactionManagement(order = 1)`
explicitamente, para garantir que o `@CacheEvict` só roda **depois** que a
transação do Postgres já deu commit. Sem essa ordem explícita, o Spring não
garante qual dos dois "embrulha" o outro, e existe uma janela real onde uma
leitura concorrente repopularia o cache com o dado antigo entre o evict e o
commit.

## Se for adicionar cache em uma entidade nova

1. Pergunte: essa entidade muda com que frequência, comparado a quantas vezes
   é lida? Se a resposta for "muda quase tanto quanto é lida", provavelmente
   não vale a pena cachear.
2. Adicione uma nova constante de nome de cache em `ConfiguracaoCacheRedis`,
   com sua própria configuração de TTL.
3. Não esqueça de evictar em **todo** método que escreve nessa entidade —
   inclusive exclusão, se existir.
4. Lembre-se: DTOs cacheados precisam ser serializáveis pelo Jackson sem
   surpresas — cuidado especial com `record`s (ver acima) e tipos de
   data/hora.
