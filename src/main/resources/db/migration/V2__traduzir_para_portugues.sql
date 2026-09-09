-- Traduz nomes de tabelas/colunas (e os valores gravados do enum de status)
-- para portugues. Segue a convencao do projeto de nunca editar uma
-- migration ja aplicada em qualquer ambiente - sempre uma nova.

ALTER TABLE product RENAME TO produto;
ALTER TABLE produto RENAME COLUMN name TO nome;
ALTER TABLE produto RENAME COLUMN type TO tipo;
ALTER TABLE produto RENAME COLUMN issuer TO emissor;
ALTER TABLE produto RENAME COLUMN issuer_rating TO classificacao_emissor;
ALTER TABLE produto RENAME COLUMN annual_rate TO taxa_anual;
ALTER TABLE produto RENAME COLUMN minimum_investment TO investimento_minimo;
ALTER TABLE produto RENAME COLUMN maturity_months TO prazo_meses;
ALTER TABLE produto RENAME COLUMN active TO ativo;
ALTER TABLE produto RENAME COLUMN created_at TO criado_em;
ALTER TABLE produto RENAME COLUMN updated_at TO atualizado_em;

ALTER TABLE account RENAME TO conta;
ALTER TABLE conta RENAME COLUMN holder_name TO nome_titular;
ALTER TABLE conta RENAME COLUMN balance TO saldo;
ALTER TABLE conta RENAME COLUMN created_at TO criado_em;
ALTER TABLE conta RENAME COLUMN updated_at TO atualizado_em;

ALTER TABLE application RENAME TO aplicacao;
ALTER TABLE aplicacao RENAME COLUMN account_id TO conta_id;
ALTER TABLE aplicacao RENAME COLUMN product_id TO produto_id;
ALTER TABLE aplicacao RENAME COLUMN amount TO valor;
ALTER TABLE aplicacao RENAME COLUMN failure_reason TO motivo_falha;
ALTER TABLE aplicacao RENAME COLUMN applied_rate TO taxa_aplicada;
ALTER TABLE aplicacao RENAME COLUMN maturity_date TO data_vencimento;
ALTER TABLE aplicacao RENAME COLUMN created_at TO criado_em;
ALTER TABLE aplicacao RENAME COLUMN updated_at TO atualizado_em;

-- O enum Java StatusAplicacao foi traduzido (PENDING/COMPLETED/FAILED ->
-- PENDENTE/CONCLUIDA/FALHOU) - sem isto, linhas existentes quebrariam ao
-- serem lidas (@Enumerated(EnumType.STRING) nao reconheceria o valor antigo).
UPDATE aplicacao SET status = 'PENDENTE' WHERE status = 'PENDING';
UPDATE aplicacao SET status = 'CONCLUIDA' WHERE status = 'COMPLETED';
UPDATE aplicacao SET status = 'FALHOU' WHERE status = 'FAILED';

ALTER INDEX idx_application_account_id RENAME TO idx_aplicacao_conta_id;
ALTER INDEX idx_account_cpf RENAME TO idx_conta_cpf;
