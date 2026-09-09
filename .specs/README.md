# invest-renda-api — Especificações

Documentação de contexto do microsserviço de investimentos em renda fixa
(CDB, LCI, LCA). Escrita para orientar qualquer pessoa (ou IA) que precise
mexer no projeto sem ter acompanhado as decisões originais.

Leia nesta ordem:

1. [`architecture.md`](./architecture.md) — stack, visão geral, estrutura de pastas.
2. [`domain-model.md`](./domain-model.md) — entidades, regras de negócio.
3. [`api-reference.md`](./api-reference.md) — todos os endpoints, request/response.
4. [`caching.md`](./caching.md) — estratégia de cache com Redis.
5. [`messaging.md`](./messaging.md) — fila RabbitMQ e o fluxo de aplicação.
6. [`database.md`](./database.md) — schema e migrations.
7. [`local-development.md`](./local-development.md) — como rodar o projeto.
8. [`docker.md`](./docker.md) — Dockerfile da API e o script `run.sh`.
9. [`testing.md`](./testing.md) — estratégia de testes.
10. [`web-frontend.md`](./web-frontend.md) — front-end Angular de validação (`invest-renda-web`) e os endpoints que existem por causa dele.
11. [`decisions.md`](./decisions.md) — decisões de arquitetura e os porquês.

## Resumo em uma frase

API REST simples para: cadastrar/editar/consultar produtos de renda fixa
(cacheados em Redis), abrir contas por CPF com saldo, e processar aplicações
(compras) de forma assíncrona via RabbitMQ, debitando o saldo da conta do
cliente.
