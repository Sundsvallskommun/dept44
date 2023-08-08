create table pet_name (
    created datetime(6),
    id bigint not null auto_increment,
    modified datetime(6),
    name varchar(255),
    primary key (id)
) engine=InnoDB;

create index pet_name_name_index on pet_name (name);
