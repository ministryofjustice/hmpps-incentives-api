CREATE TABLE prison_incentive_level
(
    id                                SERIAL,
    level_code                        VARCHAR(6)  NOT NULL,
    prison_id                         VARCHAR(6)  NOT NULL CHECK (length(prison_id) >= 1),
    active                            BOOLEAN     NOT NULL DEFAULT TRUE,
    default_on_admission              BOOLEAN     NOT NULL DEFAULT FALSE, -- NB: not imposed unique-per-prison
    when_created                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    when_updated                      TIMESTAMPTZ NOT NULL DEFAULT now(),

    remand_transfer_limit_in_pence    INTEGER     NOT NULL CHECK (remand_transfer_limit_in_pence >= 0),
    remand_spend_limit_in_pence       INTEGER     NOT NULL CHECK (remand_spend_limit_in_pence >= 0),
    convicted_transfer_limit_in_pence INTEGER     NOT NULL CHECK (convicted_transfer_limit_in_pence >= 0),
    convicted_spend_limit_in_pence    INTEGER     NOT NULL CHECK (convicted_spend_limit_in_pence >= 0),

    visit_orders                      SMALLINT    NOT NULL CHECK (visit_orders >= 0),
    privileged_visit_orders           SMALLINT    NOT NULL CHECK (privileged_visit_orders >= 0),

    PRIMARY KEY (id),
    CONSTRAINT prison_incentive_level_unique UNIQUE (level_code, prison_id),
    CONSTRAINT incentive_level_fkey FOREIGN KEY (level_code) REFERENCES incentive_level (code)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
);
CREATE INDEX prison_incentive_level_pkey_idx ON prison_incentive_level (level_code, prison_id);
CREATE INDEX prison_incentive_level_level_code_idx ON prison_incentive_level (level_code);
CREATE INDEX prison_incentive_level_prison_id_idx ON prison_incentive_level (prison_id);
CREATE INDEX prison_incentive_level_active_idx ON prison_incentive_level (active);
