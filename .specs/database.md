# Banco de dados

PostgreSQL 16, gerenciado via **Flyway** — schema nunca é criado/alterado
automaticamente pelo Hibernate (`spring.jpa.hibernate.ddl-auto: validate`).
Isso é proposital: em produção, quem manda no schema são os scripts versionados
em `src/main/resources/db/migration/`, não o mapeamento das entidades. O
Hibernate só valida que o schema real bate com o que as entidades esperam —
se divergir, a aplicação falha ao subir (rápido e explícito) em vez de gerar
DDL surpresa em silêncio.

## Migrations existentes

`V1__create_tables.sql` — cria as três tabelas originais (em inglês:
`product`, `account`, `application`).

`V2__traduzir_para_portugues.sql` — renomeia tabelas e colunas para
português (`produto`, `conta`, `aplicacao`) via `ALTER TABLE ... RENAME`, e
migra os valores gravados do enum de status (`PENDING`/`COMPLETED`/`FAILED`
→ `PENDENTE`/`CONCLUIDA`/`FALHOU`) — sem isso, linhas existentes quebrariam
ao serem lidas pelo enum Java traduzido. Ver a decisão completa (e por que
os nomes de tabela/coluna são só uma parte do que foi traduzido) em
[`decisions.md`](./decisions.md).

Estado atual das tabelas (depois das duas migrations):

- `produto`
- `conta`
- `aplicacao` (com FKs para `conta` e `produto`)

Índices: `idx_aplicacao_conta_id` (consultas de aplicações por conta) e
`idx_conta_cpf` (a própria coluna já é `UNIQUE`, o índice é redundante com
a constraint mas documenta a intenção de busca por CPF).

IDs são `UUID` gerados por `gen_random_uuid()` (função nativa do Postgres
desde a v13, não precisa da extensão `pgcrypto`).

## Adicionando uma nova migration

1. Crie `V3__descricao_curta.sql` em `src/main/resources/db/migration/`
   (Flyway ordena pelo número — nunca reescreva uma migration já aplicada em
   qualquer ambiente, sempre crie uma nova).
2. Se a mudança também afeta uma entidade JPA, atualize a entidade
   correspondente em `domain/` para bater exatamente com o novo schema —
   lembre que `ddl-auto: validate` vai reclamar de qualquer divergência.
3. Rode a aplicação localmente para o Flyway aplicar a migration e o
   Hibernate validar o schema.

## Conexão local

Ver [`local-development.md`](./local-development.md) para os detalhes de
porta/credenciais do container.
