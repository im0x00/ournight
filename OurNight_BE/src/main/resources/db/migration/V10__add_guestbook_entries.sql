create table guestbook_entries (
    created_at timestamp(6) not null,
    id UUID not null,
    store_id UUID not null,
    user_id UUID not null,
    author_snapshot varchar(100) not null,
    content varchar(120) not null,
    primary key (id)
);

create table guestbook_entry_tags (
    entry_id UUID not null,
    tag varchar(30) not null
);

alter table if exists guestbook_entries
    add constraint fk_guestbook_entries_store
    foreign key (store_id)
    references stores;

alter table if exists guestbook_entries
    add constraint fk_guestbook_entries_user
    foreign key (user_id)
    references users;

alter table if exists guestbook_entry_tags
    add constraint fk_guestbook_entry_tags_entry
    foreign key (entry_id)
    references guestbook_entries;

create index if not exists idx_guestbook_entries_store_created_at
    on guestbook_entries (store_id, created_at desc);
