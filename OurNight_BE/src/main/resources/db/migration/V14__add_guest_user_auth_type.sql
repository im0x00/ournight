alter table users
    alter column kakao_id drop not null;

alter table users
    add column if not exists auth_type varchar(255) not null default 'KAKAO';

alter table users
    drop constraint if exists users_role_check;

alter table users
    add constraint users_role_check check (role in ('USER', 'ADMIN', 'GUEST'));

alter table users
    drop constraint if exists users_auth_type_check;

alter table users
    add constraint users_auth_type_check check (auth_type in ('KAKAO', 'GUEST'));
