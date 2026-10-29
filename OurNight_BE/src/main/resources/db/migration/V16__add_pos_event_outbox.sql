create table pos_event_outbox (
    attempts integer not null default 0,
    created_at timestamp(6) with time zone not null,
    next_attempt_at timestamp(6) with time zone not null,
    published_at timestamp(6) with time zone,
    id UUID not null,
    store_id UUID not null,
    event_type varchar(80) not null,
    redis_stream_id varchar(80),
    last_error TEXT,
    payload TEXT not null,
    primary key (id)
);

create index idx_pos_event_outbox_publishable
    on pos_event_outbox (published_at, next_attempt_at, created_at);

create index idx_pos_event_outbox_store_created
    on pos_event_outbox (store_id, created_at);
