create table waiting_statuses (
    id uuid not null,
    name varchar(100) not null,
    count_value integer not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (id),
    constraint uk_waiting_status_name unique (name)
);

insert into waiting_statuses (id, name, count_value, created_at, updated_at) values
('a6a2ae91-b2c9-4d6a-8dd1-05cefe77ef01', '신소재공학관 앞', 0, current_timestamp, current_timestamp),
('b8d4e602-e1cc-4ad6-8df0-5a2333dc8892', '사회과학관 앞', 0, current_timestamp, current_timestamp),
('c35259c4-02a3-49f8-aa2f-9f8fe91e8a03', '국제관 옆', 0, current_timestamp, current_timestamp),
('d9c2cfe5-b712-4582-8555-387a55b325f9', 'GATE A', 0, current_timestamp, current_timestamp),
('e02939fb-9ab2-41ee-aef0-6257252f7f24', 'GATE B', 0, current_timestamp, current_timestamp),
('f18b6395-7a1f-41b9-b32d-9a6627f64f19', 'GATE C', 0, current_timestamp, current_timestamp);
