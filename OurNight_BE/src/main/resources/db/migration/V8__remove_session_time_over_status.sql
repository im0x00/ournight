update table_sessions
set status = 'ACTIVE'
where status = 'TIME_OVER';

alter table table_sessions
    drop constraint if exists table_sessions_status_check;

alter table table_sessions
    add constraint table_sessions_status_check
        check (status in ('ACTIVE','CLOSED'));

do $$
begin
    if exists (
        select 1
        from table_sessions
        where status = 'ACTIVE'
        group by store_table_id
        having count(*) > 1
    ) then
        raise exception 'Cannot add uk_table_sessions_active_table: duplicate ACTIVE table sessions exist';
    end if;
end $$;

create unique index if not exists uk_table_sessions_active_table
    on table_sessions (store_table_id)
    where status = 'ACTIVE';
