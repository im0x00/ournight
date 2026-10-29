alter table venues
    alter column latitude type double precision using latitude::double precision,
    alter column longitude type double precision using longitude::double precision;
