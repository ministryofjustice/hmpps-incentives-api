create table prisoner_iep_level
(
    id              SERIAL PRIMARY KEY,
    booking_id      numeric                  not null,
    sequence        integer                  not null,
    prisoner_number varchar(10)              not null,
    prison_id       varchar(6)               not null,
    location_id     varchar(30)              not null,
    review_time     timestamp with time zone not null,
    iep_code        varchar(6)               not null,
    comment_text    text,
    reviewed_by     varchar(30)              not null,
    current         bool                     not null default true,
    when_created    timestamp with time zone not null default now(),
    CONSTRAINT fk_iep_prison
        FOREIGN KEY (prison_id, iep_code)
            REFERENCES iep_prison (prison_id, iep_code),
    unique (booking_id, sequence)
);

create index prisoner_iep_level_fk_idx on prisoner_iep_level (prison_id, iep_code);

create index prisoner_iep_level_uqx on prisoner_iep_level (booking_id, sequence);

create index prisoner_iep_level_idx1 on prisoner_iep_level (prisoner_number);

create index prisoner_iep_level_loc_idx on prisoner_iep_level (location_id);
