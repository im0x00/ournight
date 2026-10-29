create table store_scraps (
    created_at timestamp(6) not null,
    id UUID not null,
    store_id UUID not null,
    user_id UUID not null,
    primary key (id),
    constraint uk_store_scraps_user_store unique (user_id, store_id)
);

alter table if exists store_scraps
    add constraint fk_store_scraps_store
    foreign key (store_id)
    references stores;

alter table if exists store_scraps
    add constraint fk_store_scraps_user
    foreign key (user_id)
    references users;

create index if not exists idx_store_scraps_user_id
    on store_scraps (user_id);

create index if not exists idx_store_scraps_store_id
    on store_scraps (store_id);
