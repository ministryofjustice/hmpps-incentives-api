create table next_review_date
(
    booking_id       numeric PRIMARY KEY      not null,
    next_review_date date                     not null,
    when_created     timestamp with time zone not null default now()
);

create index next_review_date_idx on next_review_date (next_review_date);
