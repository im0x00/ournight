alter table orders
    add column if not exists time_slot_id UUID;

update orders o
set time_slot_id = (
    select sts.id
    from table_sessions ts
    join store_tables st on st.id = ts.store_table_id
    join store_time_slots sts on sts.store_id = st.store_id
    where ts.id = o.session_id
      and sts.start_time <= o.ordered_at
      and sts.end_time > o.ordered_at
    order by sts.start_time desc, sts.id
    limit 1
)
where o.time_slot_id is null;

update orders o
set time_slot_id = (
    select sts.id
    from table_sessions ts
    join store_tables st on st.id = ts.store_table_id
    join store_time_slots sts on sts.store_id = st.store_id
    where ts.id = o.session_id
    order by abs(extract(epoch from (sts.start_time - o.ordered_at))), sts.id
    limit 1
)
where o.time_slot_id is null;

with fallback_slots as (
    select
        st.store_id,
        min(o.ordered_at) as start_time,
        greatest(max(o.ordered_at) + interval '1 minute', min(o.ordered_at) + interval '1 minute') as end_time
    from orders o
    join table_sessions ts on ts.id = o.session_id
    join store_tables st on st.id = ts.store_table_id
    where o.time_slot_id is null
      and not exists (
          select 1
          from store_time_slots existing
          where existing.store_id = st.store_id
      )
    group by st.store_id
)
insert into store_time_slots (has_waiting, end_time, start_time, id, store_id)
select
    false,
    fs.end_time,
    fs.start_time,
    fs.store_id,
    fs.store_id
from fallback_slots fs
where not exists (
    select 1
    from store_time_slots existing
    where existing.id = fs.store_id
);

update orders o
set time_slot_id = (
    select sts.id
    from table_sessions ts
    join store_tables st on st.id = ts.store_table_id
    join store_time_slots sts on sts.store_id = st.store_id
    where ts.id = o.session_id
    order by abs(extract(epoch from (sts.start_time - o.ordered_at))), sts.id
    limit 1
)
where o.time_slot_id is null;

alter table orders
    alter column time_slot_id set not null;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'fk_orders_time_slot'
    ) then
        alter table orders
            add constraint fk_orders_time_slot
                foreign key (time_slot_id)
                references store_time_slots;
    end if;
end $$;

create index if not exists idx_orders_time_slot_status_ordered_at
    on orders (time_slot_id, status, ordered_at desc, id desc);
