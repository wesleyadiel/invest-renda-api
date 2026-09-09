# Front-end de validação (`invest-renda-web`)

Projeto irmão, em `../invest-renda-web` (fora deste repositório Maven,
`ng new` separado). Um front-end Angular simples, sem login e sem
autenticação, cuja única finalidade é exercitar manualmente todos os fluxos
da API e dar visibilidade sobre o que normalmente fica "escondido" atrás de
`curl`/Postman: o status real da fila (RabbitMQ) e do cache (Redis).

Não é um produto — é ferramenta de QA/demonstração. Detalhes de como rodar
e o que cada tela faz estão no `README.md` do próprio projeto.

## Por que a API ganhou endpoints novos por causa disso

Antes desse front-end, a API não tinha como listar todas as contas nem
todas as aplicações (só buscar uma de cada vez, por CPF/id) — fazia sentido
para uma API "poucos endpoints", mas uma tela de validação precisa de tabelas
para escolher o que testar. Endpoints adicionados especificamente para isso:

- `GET /contas` — lista todas as contas (`ContaController`/`ContaService`).
- `GET /aplicacoes` — lista todas as aplicações, mais recente primeiro,
  com `contaCpf` e `produtoNome` já resolvidos (join fetch em
  `AplicacaoRepository.buscarTodasOrdenadasPorDataCriacaoDesc()`, para a
  tela não precisar cruzar IDs manualmente).
- `GET /operacoes/fila` — consulta ao vivo via
  `AmqpAdmin.getQueueProperties(...)` o tamanho e o número de consumidores
  da fila de aplicações e da DLQ. Não guarda nem simula nada, é sempre o
  estado real do RabbitMQ na hora da chamada.
- `GET /operacoes/cache` — consulta ao vivo via `StringRedisTemplate` se a
  listagem de produtos está em cache (e o TTL restante) e quais produtos
  têm detalhe em cache (`KEYS produto::*` + TTL de cada um). Mesma lógica:
  reflete o Redis real, não um estado paralelo.

`OperacoesController` (`controller/OperacoesController.java`) deixa claro no
javadoc que esses dois endpoints são só para observabilidade/validação, não
fazem parte do domínio de negócio da API.

## CORS

`config/ConfiguracaoCors.java` libera qualquer origem `http://localhost:*`
(não uma porta fixa) — o dev server do Angular muda de porta sozinho quando
a 4200 padrão já está ocupada por outro projeto, e travar em uma porta
específica gerou esse exato problema durante o desenvolvimento. Como não há
autenticação nem dado sensível em jogo, liberar qualquer porta local não
introduz um risco real.
