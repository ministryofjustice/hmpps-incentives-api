create table iep_level
(
    iep_code        varchar(6)               not null
        constraint iep_level_pkey primary key,
    iep_description varchar(30)              not null,
    sequence        int                      not null default 99,
    active          bool                     not null default true,
    when_created    timestamp with time zone not null default now()
);
