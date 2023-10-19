create table kpi
(
    day DATE PRIMARY KEY NOT NULL,

    -- number of prisoners overdue a review
    overdue_reviews                   INTEGER NOT NULL CHECK (overdue_reviews >= 0),
    -- reviews conducted in the previous month
    previous_month_reviews_conducted  INTEGER NOT NULL CHECK (previous_month_reviews_conducted >= 0),
    -- prisoners reviewed in the previous month
    previous_month_prisoners_reviewed INTEGER NOT NULL CHECK (previous_month_prisoners_reviewed >= 0),

    when_created TIMESTAMPTZ NOT NULL DEFAULT now(),
    when_updated TIMESTAMPTZ NOT NULL DEFAULT now()
);

create index day_idx on kpi (day);
