create table pet_image (
    created datetime(6),
    id bigint not null auto_increment,
    modified datetime(6),
    pet_name_id bigint not null,
    file_name varchar(255),
    mime_type varchar(255),
    content longblob,
    primary key (id)
) engine=InnoDB;
   
alter table if exists pet_image 
   add constraint fk_pet_image_pet_name_id_pet_name_id 
   foreign key (pet_name_id) 
   references pet_name (id);