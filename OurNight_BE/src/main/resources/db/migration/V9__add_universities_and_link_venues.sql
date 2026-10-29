create table universities (
    id UUID not null,
    name varchar(100) not null,
    latitude double precision not null,
    longitude double precision not null,
    primary key (id)
);

alter table venues
    add column university_id UUID;

alter table if exists venues
    add constraint FK_venues_university
    foreign key (university_id)
    references universities;
