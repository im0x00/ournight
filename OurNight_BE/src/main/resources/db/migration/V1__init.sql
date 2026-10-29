-- V1: initial schema (JPA/Hibernate metadata export). Regenerate: profile `schemaexport` + build/V1__init_raw.sql

    create table call_option (
        is_active boolean not null,
        id uuid not null,
        store_id UUID,
        name varchar(255),
        primary key (id)
    );

    create table call_request (
        created_at timestamp(6),
        call_option_id uuid,
        id uuid not null,
        table_id UUID,
        table_session_id UUID,
        status varchar(255) check (status in ('PENDING','COMPLETED')),
        primary key (id)
    );

    create table issue (
        after_amount numeric(19,2) not null,
        after_quantity integer,
        original_amount numeric(19,2) not null,
        original_quantity integer,
        table_number_snapshot integer not null,
        closed_at timestamp(6),
        opened_at timestamp(6) not null,
        client_request_id UUID not null,
        id UUID not null,
        order_id UUID not null,
        order_item_id UUID,
        store_id UUID not null,
        time_slot_id UUID not null,
        menu_summary_snapshot varchar(300) not null,
        reject_reason varchar(500),
        kind varchar(255) not null check (kind in ('REFUND','DECREASE')),
        reason_type varchar(255) not null check (reason_type in ('CUSTOMER_REQUEST','STORE_ISSUE','OUT_OF_STOCK','VOID','OTHER')),
        status varchar(255) not null check (status in ('OPEN','EXECUTED','REJECTED')),
        target_type varchar(255) not null check (target_type in ('ORDER','ORDER_ITEM')),
        primary key (id),
        constraint uk_issue_order_client_request unique (order_id, client_request_id)
    );

    create table menu_badges (
        menu_id UUID not null,
        badge varchar(255) check (badge in ('BEST','NEW','POPULAR'))
    );

    create table menu_sales_summaries (
        base_date date not null,
        total_amount numeric(38,2) not null,
        total_quantity integer not null,
        version bigint not null,
        id uuid not null,
        store_id UUID not null,
        store_menu_id UUID not null,
        time_slot_id UUID not null,
        menu_name varchar(255) not null,
        primary key (id),
        constraint uk_menu_sales_slot unique (store_id, time_slot_id, store_menu_id)
    );

    create table order_item_changes (
        after_qty integer not null,
        before_qty integer not null,
        delta integer not null,
        created_at timestamp(6) not null,
        id UUID not null,
        order_item_id UUID not null,
        type varchar(30) not null check (type in ('DECREASED','CANCELED')),
        reason_type varchar(50) check (reason_type in ('CUSTOMER_REQUEST','STORE_ISSUE','OUT_OF_STOCK','VOID','OTHER')),
        primary key (id)
    );

    create table order_items (
        price numeric(38,2) not null,
        quantity integer not null,
        version bigint not null,
        id UUID not null,
        order_id UUID not null,
        store_menu_id UUID not null,
        status varchar(30) not null check (status in ('WAITING','READY','COOKING','COOKED','SERVED','CANCELED')),
        name varchar(100) not null,
        primary key (id)
    );

    create table orders (
        cart_version integer not null,
        session_order_sequence integer not null,
        total_amount numeric(38,2),
        completed_at timestamp(6),
        ordered_at timestamp(6) not null,
        version bigint not null,
        commit_id UUID not null,
        id UUID not null,
        session_id UUID not null,
        status varchar(255) not null check (status in ('PENDING_PAYMENT','PAYMENT_REQUESTED','CONFIRMED','COMPLETED','CANCELED')),
        primary key (id),
        constraint uk_order_commit unique (session_id, commit_id)
    );

    create table pass_keys (
        updated_at timestamp(6),
        id uuid not null,
        slot_id uuid not null unique,
        type varchar(255) not null check (type in ('PIN','QR')),
        value varchar(255) not null,
        primary key (id)
    );

    create table passes (
        activated_at timestamp(6),
        expired_at timestamp(6),
        id UUID not null,
        slot_id uuid not null,
        user_id uuid not null,
        status varchar(255) not null check (status in ('INACTIVE','ACTIVATED','EXPIRED')),
        primary key (id)
    );

    create table session_participants (
        joined_at timestamp(6) not null,
        id UUID not null,
        pass_id UUID not null,
        session_id UUID not null,
        primary key (id),
        constraint uk_session_participant_session_pass unique (pass_id)
    );

    create table store_images (
        store_id UUID not null,
        image_url TEXT
    );

    create table store_menu_category (
        sequence integer not null,
        updated_at timestamp(6) with time zone not null,
        id uuid not null,
        store_id UUID not null,
        name varchar(255) not null,
        primary key (id)
    );

    create table store_menus (
        is_recommended boolean not null,
        is_unlimited boolean not null,
        requires_cooking boolean not null,
        sale_price numeric(38,2) not null,
        sequence integer not null,
        updated_at timestamp(6) with time zone not null,
        category_id uuid not null,
        id UUID not null,
        store_id UUID not null,
        venue_menu_id UUID,
        description varchar(500),
        image_url varchar(255),
        name varchar(255) not null,
        status varchar(255) not null check (status in ('ON_SALE','NOT_AVAILABLE')),
        primary key (id)
    );

    create table store_notices (
        store_id UUID not null,
        content varchar(255)
    );

    create table store_sales_summaries (
        base_date date not null,
        refund_amount numeric(38,2) not null,
        time_quantum integer not null,
        total_sales_amount numeric(38,2) not null,
        order_count bigint not null,
        version bigint not null,
        id uuid not null,
        store_id UUID not null,
        time_slot_id UUID not null,
        primary key (id),
        constraint uk_store_sales_slot_quantum unique (store_id, time_slot_id, time_quantum)
    );

    create table store_table_zones (
        display_order integer not null,
        is_active boolean not null,
        id UUID not null,
        store_id UUID not null,
        name varchar(100) not null,
        primary key (id),
        constraint uk_store_zone_name unique (store_id, name)
    );

    create table store_tables (
        capacity integer not null,
        table_number integer not null,
        x_position float(53) not null,
        y_position float(53) not null,
        id UUID not null,
        store_id UUID not null,
        zone_id UUID,
        qr_image_url varchar(500),
        qr_link varchar(500),
        status varchar(255) check (status in ('VACANT','OCCUPIED')),
        primary key (id),
        constraint uk_store_table_number unique (store_id, table_number)
    );

    create table store_time_slots (
        has_waiting boolean not null,
        end_time timestamp(6) not null,
        start_time timestamp(6) not null,
        id uuid not null,
        store_id UUID not null,
        primary key (id)
    );

    create table stores (
        entrance_fee numeric(12,0),
        is_active boolean not null,
        time_limit integer not null,
        created_at timestamp(6),
        id UUID not null,
        user_id UUID not null,
        venue_id UUID,
        type varchar(20) not null,
        account_number varchar(50),
        bank_name varchar(50),
        name varchar(100) not null,
        description varchar(500) not null,
        primary key (id)
    );

    create table table_sessions (
        session_order_sequence integer not null,
        ended_at timestamp(6),
        expires_at timestamp(6),
        started_at timestamp(6) not null,
        version bigint not null,
        id UUID not null,
        merged_into_session_id UUID,
        store_table_id UUID not null,
        status varchar(30) not null check (status in ('ACTIVE','CLOSED')),
        primary key (id)
    );

    create table users (
        kakao_id bigint not null unique,
        id uuid not null,
        nickname varchar(255) not null,
        role varchar(255) check (role in ('USER','ADMIN')),
        primary key (id)
    );

    create table venue_menus (
        price numeric(38,2) not null,
        id UUID not null,
        venue_id UUID not null,
        image_url varchar(255),
        original_name varchar(128) NOT NULL,
        primary key (id)
    );

    create table venues (
        is_partner boolean not null,
        latitude float(53) not null,
        longitude float(53) not null,
        id UUID not null,
        name varchar(100) not null,
        address varchar(200) not null,
        detail_location varchar(200),
        primary key (id)
    );

    alter table if exists call_option 
       add constraint FK4pmmjhd6womag0v7x0nfxt390 
       foreign key (store_id) 
       references stores;

    alter table if exists call_request 
       add constraint FKqpsbwe9pre2rcmn3e51gvrp4p 
       foreign key (call_option_id) 
       references call_option;

    alter table if exists call_request 
       add constraint FK1h5dbjgxas8n99pgep97bg240 
       foreign key (table_id) 
       references store_tables;

    alter table if exists call_request 
       add constraint FK353b6j1s2qetqcs06aujvtcwx 
       foreign key (table_session_id) 
       references table_sessions;

    alter table if exists menu_badges 
       add constraint FK9m3bc0l710ivaq7gyql3f4is6 
       foreign key (menu_id) 
       references store_menus;

    alter table if exists order_item_changes 
       add constraint FKsqehd4yxlihr6ilf1y4711r4p 
       foreign key (order_item_id) 
       references order_items;

    alter table if exists order_items 
       add constraint FKcqr2gye4yjunsq9r7303wb709 
       foreign key (store_menu_id) 
       references store_menus;

    alter table if exists order_items 
       add constraint FKbioxgbv59vetrxe0ejfubep1w 
       foreign key (order_id) 
       references orders;

    alter table if exists orders 
       add constraint FK6020t7ng0ram4bb3hludac40g 
       foreign key (session_id) 
       references table_sessions;

    alter table if exists passes 
       add constraint FKao9e91bqt8qsomrcj7pefxq8s 
       foreign key (slot_id) 
       references store_time_slots;

    alter table if exists passes 
       add constraint FK18qdlko1thaqtdent5w2cl00p 
       foreign key (user_id) 
       references users;

    alter table if exists session_participants 
       add constraint FKca80k24slmklgqfnukebkq1o2 
       foreign key (pass_id) 
       references passes;

    alter table if exists session_participants 
       add constraint FK8cynob92gpj3vlvhq6b7y6dwd 
       foreign key (session_id) 
       references table_sessions;

    alter table if exists store_images 
       add constraint FK6s8ktg0dkmr6w1wc8jqwvk6tu 
       foreign key (store_id) 
       references stores;

    alter table if exists store_menu_category 
       add constraint FKbvp13cncgbk0i5sb7d2wjkjw 
       foreign key (store_id) 
       references stores;

    alter table if exists store_menus 
       add constraint FKt8eb2m2lm2ifx11if0r2jgxsv 
       foreign key (category_id) 
       references store_menu_category;

    alter table if exists store_menus 
       add constraint FKtnc6cftp4bubdsl23tifuh80b 
       foreign key (store_id) 
       references stores;

    alter table if exists store_menus 
       add constraint FKo8lemhj8eahpqj90dke3a0du3 
       foreign key (venue_menu_id) 
       references venue_menus;

    alter table if exists store_notices 
       add constraint FKpgb3l5w1wl4htsx6qyxetcwk2 
       foreign key (store_id) 
       references stores;

    alter table if exists store_table_zones 
       add constraint FKbdnlqjsdpayi9xjx7dl1x0eni 
       foreign key (store_id) 
       references stores;

    alter table if exists store_tables 
       add constraint FKiv2sjsih7vy9by0dj2vqtkoc6 
       foreign key (store_id) 
       references stores;

    alter table if exists store_tables 
       add constraint FKihr5x6o1okoa4rnxtiplmf5vr 
       foreign key (zone_id) 
       references store_table_zones;

    alter table if exists store_time_slots 
       add constraint FKt89ytqk1a16immfqyq3rtnwmr 
       foreign key (store_id) 
       references stores;

    alter table if exists stores 
       add constraint FKiw281hibigo41ijsvot42osjj 
       foreign key (user_id) 
       references users;

    alter table if exists stores 
       add constraint FK7nwvuo6bs1kwl1oojebwkut8p 
       foreign key (venue_id) 
       references venues;

    alter table if exists table_sessions 
       add constraint FKf1r1oyfibp9i7wqjvmy1ci9kj 
       foreign key (store_table_id) 
       references store_tables;

    alter table if exists venue_menus 
       add constraint FKaaxxhooocv1rc69f7dmxf25cq 
       foreign key (venue_id) 
       references venues;
