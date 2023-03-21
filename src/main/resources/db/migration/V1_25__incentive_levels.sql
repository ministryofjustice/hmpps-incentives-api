CREATE TABLE incentive_level
(
    code         VARCHAR(6)  NOT NULL PRIMARY KEY CHECK (length(code) >= 1),
    description  VARCHAR(30) NOT NULL CHECK (length(description) >= 1),
    "sequence"   SMALLINT    NOT NULL, -- not imposing unique to allow reordering
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    when_created TIMESTAMPTZ NOT NULL DEFAULT now(),
    when_updated TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX incentive_level_sequence_idx ON incentive_level ("sequence");
CREATE INDEX incentive_level_active_idx ON incentive_level (active);

INSERT INTO incentive_level (code, description, "sequence", active)
VALUES ('BAS', 'Basic', 1, true),
       ('STD', 'Standard', 2, true),
       ('ENH', 'Enhanced', 3, true),
       ('EN2', 'Enhanced 2', 4, true),
       ('EN3', 'Enhanced 3', 5, true),
       ('ENT', 'Entry', 99, false);

ALTER TABLE prisoner_iep_level
    ADD CONSTRAINT incentive_level_fkey
        FOREIGN KEY (iep_code)
            REFERENCES incentive_level (code)
            ON DELETE RESTRICT
            ON UPDATE RESTRICT;
