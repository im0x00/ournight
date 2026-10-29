alter table table_sessions
    drop constraint if exists table_sessions_status_check;

alter table table_sessions
    add column if not exists time_slot_id UUID;

update table_sessions ts
set time_slot_id = (
    select sts.id
    from store_tables st
    join store_time_slots sts on sts.store_id = st.store_id
    where st.id = ts.store_table_id
      and sts.start_time <= ts.started_at
      and sts.end_time > ts.started_at
    order by sts.start_time desc, sts.id
    limit 1
)
where ts.time_slot_id is null;

update table_sessions ts
set time_slot_id = (
    select sts.id
    from store_tables st
    join store_time_slots sts on sts.store_id = st.store_id
    where st.id = ts.store_table_id
    order by abs(extract(epoch from (sts.start_time - ts.started_at))), sts.id
    limit 1
)
where ts.time_slot_id is null;

alter table table_sessions
    alter column time_slot_id set not null;

alter table table_sessions
    add constraint fk_table_sessions_time_slot
        foreign key (time_slot_id)
        references store_time_slots;

alter table table_sessions
    add constraint table_sessions_status_check
        check (status in ('ACTIVE','TIME_OVER','CLOSED'));

create index if not exists idx_table_sessions_slot_status
    on table_sessions (time_slot_id, status);
