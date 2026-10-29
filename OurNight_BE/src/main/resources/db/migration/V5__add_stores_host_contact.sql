alter table stores
    add column if not exists host_contact varchar(50) not null default '';
