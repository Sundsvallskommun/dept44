
    create table pet_name (
       id bigint not null auto_increment,
        created datetime(6),
        modified datetime(6),
        name varchar(255),
        primary key (id)
    ) engine=InnoDB;
create index pet_name_name_index on pet_name (name);
