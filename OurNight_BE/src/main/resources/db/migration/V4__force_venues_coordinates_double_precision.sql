alter table venues
    alter column latitude drop not null,
    alter column longitude drop not null;

update venues
set latitude = null,
    longitude = null;

alter table venues
    alter column latitude type double precision using coalesce(latitude::double precision, 0.0),
    alter column longitude type double precision using coalesce(longitude::double precision, 0.0);

update venues
set latitude = 0.0
where latitude is null;

update venues
set longitude = 0.0
where longitude is null;

alter table venues
    alter column latitude set default 0.0,
    alter column longitude set default 0.0,
    alter column latitude set not null,
    alter column longitude set not null;
