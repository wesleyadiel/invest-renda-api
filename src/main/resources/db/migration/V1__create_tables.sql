CREATE TABLE product (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(120)    NOT NULL,
    type                VARCHAR(20)     NOT NULL,               -- CDB, LCI, LCA
    issuer              VARCHAR(120)    NOT NULL,
    issuer_rating       VARCHAR(10)     NOT NULL,
    annual_rate         NUMERIC(7, 4)   NOT NULL,                -- taxa a.a. em %
    minimum_investment  NUMERIC(18, 2)  NOT NULL,
    maturity_months     INTEGER         NOT NULL,
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE account (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cpf         VARCHAR(11)     NOT NULL UNIQUE,
    holder_name VARCHAR(120)    NOT NULL,
    balance     NUMERIC(18, 2)  NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE application (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID            NOT NULL REFERENCES account(id),
    product_id      UUID            NOT NULL REFERENCES product(id),
    amount          NUMERIC(18, 2)  NOT NULL,
    status          VARCHAR(20)     NOT NULL,                    -- PENDING, COMPLETED, FAILED
    failure_reason  VARCHAR(255),
    applied_rate    NUMERIC(7, 4),
    maturity_date   DATE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_application_account_id ON application(account_id);
CREATE INDEX idx_account_cpf ON account(cpf);
