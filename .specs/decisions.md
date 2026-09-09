# Decisões de arquitetura e trade-offs

Registro do porquê das escolhas não óbvias do projeto. Objetivo: quem for
mexer depois (humano ou IA) não repete uma discussão já resolvida, nem
desfaz sem querer uma decisão que existia por um motivo específico.

> **Nota sobre nomes de classe neste arquivo**: em 2026-09-09 todo o
> código (classes, métodos, campos, tabelas/colunas do banco, JSON da API)
> foi traduzido para português — ver a decisão logo abaixo. As entradas
> **anteriores** a essa data podem citar nomes de classe em inglês que já
> não existem mais (`ProductService`, `ApiResponse`, `GlobalExceptionHandler`
> etc.) — o raciocínio de cada decisão continua válido, só o nome mudou.
> Não foram reescritas para não distorcer o registro histórico de quando
> cada decisão foi tomada.

## Tradução completa do código para português (PT-BR)

Todo identificador do projeto — classes, métodos, campos, tabelas/colunas
do Postgres, filas/exchange do RabbitMQ, chaves de cache do Redis, o JSON
exposto pela API e o front-end Angular — foi traduzido para português.
Antes disso, só comentários/mensagens estavam em português; o código em si
(nomes de classe, campos, endpoints) estava em inglês, como é comum em
projetos brasileiros.

**O que ficou em inglês, de propósito**:
- **Sufixos arquiteturais do Spring/Java** (`Controller`, `Service`,
  `Repository`, `Request`, `Response`, `Exception`, `Filter`, `Config`,
  `Properties`, `Handler`) — são termos do framework/padrão de projeto, não
  vocabulário de negócio. `ProductController` virou `ProdutoController`
  (traduz o domínio, mantém o padrão), não `ProdutoControlador`.
- **`InvestRendaApiApplication`** (classe de bootstrap do Spring Boot) —
  ficou em inglês por dois motivos: é convenção do próprio Spring Boot
  (`<Nome>Application`), e traduzir criaria colisão conceitual com a nossa
  entidade de domínio `Aplicacao` (duas coisas chamadas "Aplicação" no
  mesmo projeto, com significados totalmente diferentes).
- **Métodos herdados de `JpaRepository`/`CrudRepository`** (`save`,
  `findById`, `findAll`, `saveAndFlush`, `deleteById`) — são a interface do
  Spring Data em si, não algo que declaramos; não dá pra renomear sem parar
  de usar o Spring Data.
- **Termos de protocolo/infraestrutura sem tradução natural e amplamente
  usados como estão mesmo em contextos 100% em português**: `cache`,
  `exchange` (AMQP), `status`, header HTTP `X-RateLimit-Remaining`.

**Restrição tecnica que exigiu cuidado**: o Spring Data JPA deriva queries
automaticamente a partir do **nome do método**, mas só reconhece palavras-
chave em inglês (`findBy`, `existsBy`, etc.). Métodos que antes dependiam
dessa derivação (`findByCpf`, `existsByCpf`) não podiam simplesmente virar
`buscarPorCpf`/`existePorCpf` sem quebrar — cada um ganhou uma `@Query` JPQL
explícita para continuar funcionando com nome em português (ver
`ContaRepository`).

**Banco de dados**: nova migration (`V2__traduzir_para_portugues.sql`, ver
[`database.md`](./database.md)) renomeia tabelas/colunas via
`ALTER TABLE ... RENAME` (preserva os dados existentes) e migra os valores
gravados do enum de status antigo para os novos (`PENDING` → `PENDENTE`,
etc.) — sem isso, `@Enumerated(EnumType.STRING)` quebraria ao ler linhas
gravadas antes da tradução, porque o valor salvo no banco não bateria com
nenhuma constante do enum Java traduzido. Testado migrando um banco real já
com dados (não só um banco novo via Testcontainers) antes de considerar a
mudança pronta.

**Front-end**: o `invest-renda-web` foi atualizado junto — nomes de
componente, arquivo, rotas (`/products` → `/produtos` etc.) e os campos dos
`interface` TypeScript, porque o contrato JSON da API mudou (isso quebraria
o front sem o acompanhamento).

## Rate limit: Bucket4j em memória, um bucket por IP

`config/RateLimitFilter.java` + `RateLimitProperties` + `RateLimitConfig`.
Escolhas:

- **Bucket4j** (não uma solução caseira nem `spring-cloud-gateway`/API
  gateway externo): é a biblioteca que o time já usa no `synke-api` (mesma
  versão, `8.10.1`), então não introduz uma ferramenta nova para aprender —
  e implementa token bucket corretamente (permite rajada até a capacidade,
  depois limita à taxa de reposição) sem reinventar a lógica de janela
  deslizante na mão.
- **Um bucket por IP do cliente** (`X-Forwarded-For` se presente, senão
  `getRemoteAddr()`), não um bucket global único — um cliente hiperativo
  não deveria conseguir consumir a cota de todo mundo. Chave de agrupamento
  simples de propósito; não tenta identificar usuário autenticado porque a
  API não tem autenticação.
- **Em memória (`ConcurrentHashMap`), não backend compartilhado
  (ex: `bucket4j-redis`)**: adequado para uma instância só da API (o cenário
  atual). Se um dia isso rodar atrás de um load balancer com múltiplas
  instâncias, cada instância teria sua própria cota — nesse ponto vale
  migrar para um bucket compartilhado no Redis (que o projeto já usa para
  cache, então a peça de infraestrutura já existiria).
- **Registrado via `FilterRegistrationBean` com `addUrlPatterns` explícito**
  (`/products/*`, `/accounts/*`, `/applications/*`, `/ops/*`), não como
  `@Component` direto — um `Filter` anotado com `@Component` seria
  auto-registrado pelo Spring Boot para **todas** as URLs, inclusive
  `/actuator/health`, que é chamado a cada poucos segundos pelo
  `HEALTHCHECK` do Docker e nunca deveria ser limitado.
- **Resposta de bloqueio no mesmo envelope `ApiResponse`** (`429`, com
  header `Retry-After`) — consistente com o resto da API, não um formato
  à parte.
- **Configurável e desligável** via `investrenda.ratelimit.*` no
  `application.yml` (`enabled`, `capacity`, `refill-tokens`,
  `refill-duration-seconds`) — default de 120 req/min por cliente, folga
  confortável acima da carga de polling do `invest-renda-web` (tela de
  Aplicações a cada 3s + tela de Fila & Cache a cada 2s com 2 chamadas =
  ~80 req/min combinadas, se as duas telas ficarem abertas ao mesmo tempo).

## Envelope padrão de resposta (`ApiResponse<T>`) e handler catch-all

Toda resposta (sucesso ou erro) passou a vir no formato
`{sucesso, data, mensagem, erros}` — `dto/ApiResponse.java`. Decisões dentro
dessa mudança:

- **Wrapping explícito em cada controller, não um `ResponseBodyAdvice`
  global.** Um `ResponseBodyAdvice` interceptaria e envelopar a resposta de
  qualquer `@RestController` automaticamente, sem tocar em cada método —
  menos código, mas a documentação gerada pelo springdoc/Swagger continuaria
  mostrando o DTO "nu" (`ProductDetailResponse`), não o envelope, porque a
  introspecção do OpenAPI acontece nos tipos declarados do método, não no
  que é escrito de fato na resposta. Preferimos cada controller retornar
  `ResponseEntity<ApiResponse<T>>` explicitamente — mais repetitivo, mas o
  Swagger fica automaticamente correto, e fica óbvio lendo o método
  controller qual vai ser o formato de saída, sem precisar saber que existe
  um interceptor global agindo por baixo.
- **`GlobalExceptionHandler` ganhou um `@ExceptionHandler(Exception.class)`
  catch-all**, alem dos handlers especificos que ja existiam
  (`NotFoundException`, `DuplicateResourceException`, `BusinessException`,
  erros de validacao). Qualquer excecao que nao caia em nenhum handler mais
  especifico (um bug, uma falha de infraestrutura que sobe sem ser
  encapsulada) agora tambem responde no envelope padrao, com `mensagem` =
  `ex.getMessage()` — nao um "erro interno do servidor" generico. Pedido
  explicito do time: mostrar a mensagem real, tratada ou nao.
- **Trade-off consciente**: expor `ex.getMessage()` de uma excecao
  verdadeiramente inesperada pode vazar detalhe interno (nome de classe,
  fragmento de driver JDBC, etc.) para quem chama a API. Aceitavel para o
  estagio atual do projeto (API de validacao/avaliacao, sem dado sensivel de
  producao). Se isso for para um ambiente onde vazamento de detalhe interno
  e uma preocupacao real, o ajuste e trivial: trocar `ex.getMessage()` por
  uma mensagem fixa nesse handler especifico, mantendo o log completo
  (`log.error("Excecao nao tratada", ex)`, ja presente) para investigacao.
  A stack trace inteira so vai para o log do servidor, nunca para a
  resposta.

## `mvn test` vs `mvn verify`: testes de integração isolados dos unitários

Ao fechar as lacunas de cobertura (service de conta sem teste, controllers
sem teste, queries JPQL customizadas nunca executadas contra banco real),
adicionamos `AccountServiceTest`, quatro testes de controller
(`@WebMvcTest`) e um teste de integração com Testcontainers
(`ApplicationRepositoryIT`, Postgres real). Os três primeiros são unitários
de verdade (Mockito, sem infraestrutura) e entram no `mvn test` normal. O
de integração é deliberadamente nomeado com sufixo `IT` (não `Test`) e
plugado ao **maven-failsafe-plugin**, rodando só em `mvn verify` — para não
forçar Docker como pré-requisito de todo `mvn test`, que até aqui era
rápido e sem dependências externas (isso já estava documentado como valor
do projeto em `testing.md`, e queríamos preservar).

**Testcontainers precisou ter a versão sobrescrita** (propriedade
`testcontainers.version` no `pom.xml`, por cima do que o Spring Boot 3.3.4
gerencia por padrão, `1.19.8`) porque essa versão não conseguia negociar a
versão de API com um Docker Desktop recente — o cliente (`docker-java`)
tenta primeiro uma versão de API antiga (`1.32`) que esse Docker já não
aceita mais (`400 Bad Request`), mesmo a CLI `docker` funcionando
normalmente (ela negocia a versão de forma diferente do cliente Java).
Confirmado testando `curl --unix-socket .../docker.sock http://localhost/v1.32/info`
diretamente (400) vs `v1.40+/info` (200). Testcontainers `1.21.4` resolve
isso. Se voltar a quebrar no futuro (Docker Desktop mais novo de novo),
o primeiro passo é tentar subir essa property antes de qualquer outra
investigação.

## Validação de CPF: dígito verificador próprio, não uma biblioteca

Até então, `AccountRequest`/`ApplicationRequest` só validavam formato
(`@Pattern(regexp = "\\d{11}")` — 11 dígitos numéricos), não se o CPF é
matematicamente válido. `"12345678900"` passava nessa validação mesmo não
sendo um CPF real.

Avaliamos três caminhos: (1) [Caelum Stella](https://github.com/caelum/stella),
a biblioteca de fato padrão no mercado BR para CPF/CNPJ, com integração
pronta para Bean Validation; (2) um validador próprio; (3) não validar o
dígito verificador, só o formato (o que já tínhamos). Escolhemos a opção 2:
`validation/CPF.java` + `validation/CpfValidator.java`, uma anotação
`@ConstraintValidator` que implementa o algoritmo padrão de módulo 11 (~40
linhas, sem estado, testável isoladamente). Motivo: o projeto só valida CPF
em um único formato, em dois DTOs — trazer uma biblioteca inteira de
validadores de documentos BR (CNPJ, boleto, título de eleitor...) para usar
uma peça pequena dela não paga o custo de mais uma dependência externa. Se o
domínio crescer para validar CNPJ (ex: dados do emissor de um produto), vale
reconsiderar o Stella.

`@CPF` convive com o `@Pattern` existente (que já garante 11 dígitos
numéricos) — mensagens de erro distintas para "formato errado" vs "CPF
inválido" ajudam o consumidor da API a entender qual dos dois problemas tem.

**Nota para quem for testar manualmente**: `"12345678900"`, `"11111111111"`
etc. agora são rejeitados. Use um CPF matematicamente válido, ex.:
`11144477735` (é o que os exemplos deste `.specs` e do `README.md` usam).

## Endpoints de listagem (`/accounts`, `/applications`) e `/ops/*`

Adicionados especificamente para dar suporte ao front-end de validação
(`invest-renda-web`) — sem eles não daria para montar tabelas nem observar
fila/cache de fora. `CORS` liberado para qualquer porta `localhost`, não uma
fixa, porque o dev server do Angular muda de porta sozinho quando a 4200
padrão já está em uso por outro projeto na máquina de quem desenvolve.
Motivação completa e o que cada endpoint faz em
[`web-frontend.md`](./web-frontend.md).

## Fila para aplicações, em vez de debitar direto no request

Requisito original do projeto: "incluir em uma fila e depois um serviço que
fica escutando essa fila e fazendo as aplicações conforme os dados do
produto". Dentro disso, as escolhas de implementação foram:

- **RabbitMQ** em vez de fila via tabela no Postgres ou lista/stream no
  Redis: fila dedicada, com DLQ e retry nativos, desacopla melhor o produtor
  (API) do consumidor.
- **Validação síncrona dividida da assíncrona**: tudo que não depende de
  concorrência em saldo (conta existe, produto existe/ativo, valor ≥
  mínimo) é validado na hora do `POST`, respondendo `404`/`422` direto —
  só a checagem de saldo (que precisa de lock) fica para o consumer.
- **Lock pessimista na conta** (`SELECT ... FOR UPDATE`) no consumer, não no
  request: centraliza a mutação de saldo em um único caminho serializado por
  conta, evitando duas aplicações concorrentes da mesma conta lerem o mesmo
  saldo "antigo" e as duas debitarem.
- **Idempotência por status**: o consumer verifica se a `Application` ainda
  está `PENDING` antes de processar. RabbitMQ garante "pelo menos uma
  entrega", então mensagens duplicadas são esperadas em cenários de falha —
  sem essa checagem, uma redelivery debitaria o saldo duas vezes.
- **Saldo insuficiente é um resultado de negócio (`FAILED`), não uma
  exceção**: se fosse exceção, cairia no mecanismo de retry/DLQ, que existe
  para falhas técnicas — tentar de novo não faz o saldo aumentar, então
  gastar tentativas nisso não faz sentido e poluiria a DLQ com casos
  esperados.
- **Consumer roda no mesmo processo da API**, não como serviço separado —
  simplicidade para o escopo atual. Se o volume de aplicações justificar
  escalar o consumo independentemente da API, extrair para um processo
  próprio é o próximo passo natural (a mensageria já desacopla isso).

Detalhes completos do fluxo em [`messaging.md`](./messaging.md).

## Cache Redis só em produtos

Produtos mudam raramente e são lidos com muito mais frequência do que são
escritos — o caso onde cache compensa. Contas e aplicações não são
cacheadas: mudam a cada operação, cache traria mais risco que ganho.

## Cache + Transação: ordem explícita de AOP

Bug real encontrado durante o desenvolvimento: `ProductService.update()` tem
`@Transactional` e `@CacheEvict` no mesmo método. Por padrão, o Spring não
garante qual das duas anotações "embrulha" a outra (ambas usam
`Ordered.LOWEST_PRECEDENCE` por padrão). Se o cache ficasse por dentro da
transação, o evict rodaria **antes do commit** no Postgres — abrindo uma
janela onde uma leitura concorrente repopularia o cache com o dado antigo,
e esse dado velho ficaria servido até o TTL expirar (10 min).

Correção: `InvestRendaApiApplication` declara
`@EnableCaching(order = 0)` e `@EnableTransactionManagement(order = 1)`
explicitamente. Ordem menor = advice mais externo, então o cache sempre
"embrulha" a transação e só age depois que o commit já aconteceu de
verdade. Validado empiricamente (update seguido de leitura reflete o dado
novo imediatamente, sem esperar TTL).

**Não remova essas anotações de ordem** achando que são redundantes — sem
elas o comportamento volta a depender de detalhes internos do Spring, não de
algo garantido pelo código.

## Serialização do cache: JSON, não binário Java

Trocamos o serializer padrão do Spring Cache + Redis
(`JdkSerializationRedisSerializer`, binário) por
`GenericJackson2JsonRedisSerializer` (JSON), para o cache ser legível/
inspecionável e não quebrar toda vez que uma classe muda de forma
incompatível com serialização Java nativa.

Dois detalhes que quebraram durante a implementação e valem registro:

1. Precisa registrar `JavaTimeModule` manualmente no `ObjectMapper` do
   serializer — sem isso, qualquer campo `Instant` (`createdAt`/`updatedAt`)
   quebra a serialização com `InvalidDefinitionException`.
2. Precisa usar `DefaultTyping.EVERYTHING`, não `NON_FINAL` — os DTOs de
   resposta são `record`s (implicitamente `final`). Com `NON_FINAL` o
   Jackson não grava informação de tipo para eles ao serializar, mas o
   `GenericJackson2JsonRedisSerializer` sempre espera essa informação ao
   desserializar. Resultado observado: escrita funcionava, leitura
   subsequente do cache quebrava com
   `Unexpected token (START_OBJECT), expected START_ARRAY`.

## `saveAndFlush` em vez de `save` nos creates

`Product`, `Account` e `Application` usam `@CreationTimestamp`/
`@UpdateTimestamp` do Hibernate, que só populam os campos no **flush**, não
no `persist()`/`save()`. Como os services constroem o DTO de resposta
imediatamente após salvar (antes do commit da transação, que só flusha no
fim), `createdAt`/`updatedAt` vinham `null` na resposta do `POST`. Trocado
para `saveAndFlush` nos três pontos de criação para garantir que a resposta
reflita os valores reais gerados pelo banco/Hibernate.

## Validação com Jakarta Bean Validation nos DTOs, não nas entidades

Os `record`s de request (`ProductRequest`, `AccountRequest`,
`ApplicationRequest`) carregam as anotações de validação
(`@NotNull`, `@Positive`, `@Pattern`, etc.), não as entidades JPA. Mantém a
validação de "formato de entrada" separada das invariantes de domínio (que
vivem no service/entidade), e evita acoplar a entidade persistente ao
formato exato de um payload HTTP.

## `Dockerfile` da API na raiz, dependências isoladas em `docker/`

Separação deliberada, não incidental: `Dockerfile` (raiz) é o artefato de
build/implantação da **nossa própria API** — o que vai efetivamente rodar em
produção. `docker/docker-compose.yml` é só o suporte de infraestrutura para
desenvolvimento local (banco, fila, cache) — nunca inclui a API. Isso evita
confundir "a imagem que a gente implanta" com "o ambiente de dependências
que a gente sobe pra desenvolver localmente", que são preocupações
diferentes mesmo que ambas usem Docker. `run.sh` conhece os dois e
orquestra ambos, mas o `Dockerfile` em si não depende do compose nem
vice-versa — dá pra buildar a imagem da API (`docker build -t
invest-renda-api .`) sem nunca ter subido `docker/docker-compose.yml`.

Como a API roda via `docker run` direto (não como serviço do compose de
dependências), a rede Docker é nomeada explicitamente
(`invest-renda-network`, declarada em `docker/docker-compose.yml`) para o
script conseguir conectar o container da API nela de forma previsível, e as
variáveis de ambiente que apontam para os outros containers
(`SPRING_DATASOURCE_URL`, `SPRING_DATA_REDIS_HOST`, `SPRING_RABBITMQ_HOST`)
são passadas na hora do `docker run`, não em um `application-docker.yml`
separado — o `application.yml` principal continua com `localhost` como
default, valendo sem mudança nenhuma tanto para quem roda a API direto na
máquina (Opção B) quanto para o container (Opção A). Detalhes em
[`docker.md`](./docker.md).

**`run.sh` na raiz, não em `docker/`**: o script orquestra tanto o
`Dockerfile` (raiz) quanto `docker/docker-compose.yml` — ficar dentro de
`docker/` sugeriria que ele é só sobre as dependências, quando na verdade
`./run.sh` (sem argumento) builda e roda a própria API também. Ficar na
raiz deixa claro, só pelo caminho, que é o ponto de entrada único do
projeto ("clonou, tem Docker, roda `./run.sh`"), sem tirar `docker-compose.yml`
do lugar — essa separação (Dockerfile de implantação vs. dependências de
dev) continua valendo, é só o script de orquestração que não mora dentro de
nenhum dos dois.

## Actuator adicionado só por causa do healthcheck do container

`spring-boot-starter-actuator` não fazia parte do escopo original — foi
adicionado especificamente para o container da API ter um endpoint real de
saúde (`/actuator/health`) que tanto o `HEALTHCHECK` do Docker Compose
quanto o painel do `run.sh` pudessem consultar. Continua sendo o
motivo original pelo qual o Actuator está no projeto — por isso a lista de
endpoints expostos (`management.endpoints.web.exposure.include`) só cresce
quando existe um uso concreto (ver `health,circuitbreakers,
circuitbreakerevents,retries,retryevents`, adicionados junto com a
integração externa resiliente, abaixo) — nunca "porque pode ser útil algum
dia". `metrics`, `env`, `beans`, etc. continuam desligados por não ter uso
aqui e por não faltar expor informação interna sem necessidade.

## Integração externa de exemplo com feature toggle + Resilience4j (retry + circuit breaker)

Adicionado para ter, no projeto, um exemplo real de chamada a uma API
externa protegida por resiliência — até então a API só falava com
Postgres/Redis/RabbitMQ locais, nunca com nada de fora. Escolhas:

- **AwesomeAPI** (`economia.awesomeapi.com.br`, cotação do dólar) como a
  chamada externa — pública, sem autenticação, tema monetário (fintech),
  sem custo de manter chave/token só para um exemplo.
- **Resilience4j** (`resilience4j-spring-boot3` + `spring-boot-starter-aop`)
  em vez de só Spring Retry — precisava de circuit breaker além de
  retry/backoff, e o Spring Retry (já usado implicitamente pelo listener do
  RabbitMQ) não tem circuit breaker nativo.
- **Ordem dos decorators**: `@Retry` por fora, `@CircuitBreaker` por dentro
  (`ServicoIntegracaoExterna.consultarCotacaoDolar`) — o `fallbackMethod`
  fica no `@Retry` (o decorator mais externo), não no `@CircuitBreaker`.
  Colocar o fallback no decorator interno foi o primeiro erro cometido aqui:
  o `@CircuitBreaker` resolvia e mascarava a exceção antes do `@Retry`
  conseguir tentar de novo, e as 3 tentativas nunca aconteciam (validado ao
  vivo: sem essa correção, a chamada falhava instantaneamente ao simular
  erro, em vez de esperar o backoff de ~3s). Com o fallback no `@Retry`,
  cada tentativa de retry passa pelo circuit breaker de verdade, e um
  circuito aberto faz as tentativas restantes falharem rápido
  (`CallNotPermittedException`) em vez de esperar o backoff inteiro.
- **Dois toggles separados, em memória** (`EstadoIntegracaoExterna`, não
  persistido, reseta a cada restart): um liga/desliga a integração
  (feature flag de produto — `PUT /integracoes/habilitar`), outro força
  falha na próxima chamada para testar retry/circuit breaker de forma
  determinística sem depender da API externa estar de fato fora do ar
  (`PUT /integracoes/simular-falha`). São tratados como duas famílias de
  exceção diferentes (`IntegracaoDesabilitadaException` vs
  `FalhaConsultaCotacaoException`) porque têm significados diferentes para
  o circuit breaker: desabilitado é um estado deliberado (fica de fora das
  estatísticas via `resilience4j.circuitbreaker.instances.cotacaoDolar.
  ignore-exceptions`), falha simulada é uma falha de verdade para fins de
  contagem (conta para abrir o circuito).
- Validado ao vivo o ciclo completo: consulta real funcionando, retry com
  backoff exponencial medido (1s, depois 2s, ~3.2s total), circuito abrindo
  após múltiplas falhas (`GET /actuator/circuitbreakers`), chamadas
  seguintes falhando rápido (~30ms, `notPermittedCalls` incrementando), e
  recuperação automática para `HALF_OPEN` após `wait-duration-in-open-state`
  (10s) e depois `CLOSED` após chamadas de teste bem-sucedidas.

## Flyway + `ddl-auto: validate`, nunca `update`

Schema é sempre definido pelos scripts versionados em `db/migration/`. O
Hibernate só confere que as entidades batem com o schema real — se
divergirem, a aplicação falha ao subir em vez de gerar DDL implícito. Em
qualquer ambiente além do dev local isso evita surpresas de schema geradas
silenciosamente.
