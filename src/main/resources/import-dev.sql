-- CREATE TABLE DreamkasOperation (
--     id bigint not null primary key,
--     operationId VARCHAR(64),
--     operationStatus VARCHAR(64),
--     externalId VARCHAR(256) NOT NULL, 
--     orderNumber VARCHAR(64),
--     account VARCHAR(255),
--     amount DOUBLE, 
--     email VARCHAR(255), 
--     phone VARCHAR(255), 
--     createAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
--     INDEX idx_order (orderNumber),
--     UNIQUE KEY uidx_externalId (externalId)
-- )

create table DreamkasOperation (id bigint not null, account varchar(255), amount float(53), createAt datetime(6), email varchar(255), externalId varchar(255), operationId varchar(255), operationStatus tinyint, orderNumber varchar(255), phone varchar(255), primary key (id)) engine=InnoDB;
create table DreamkasOperation_SEQ (next_val bigint) engine=InnoDB;
insert into DreamkasOperation_SEQ values ( 1 );