create table iep_prison
(
    id           SERIAL                   PRIMARY KEY,
    prison_id    varchar(6)               not null,
    iep_code     varchar(6)               not null,
    active       bool                     not null default true,
    expiry_date  timestamp with time zone,
    default_iep  bool                     not null default false,
    when_created timestamp with time zone not null default now(),
        unique (prison_id, iep_code)
);

create index iep_prison_fk_idx on iep_prison (prison_id);

