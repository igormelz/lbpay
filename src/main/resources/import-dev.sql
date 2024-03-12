-- Active: 1709545947113@@127.0.0.1@3306@lbpay

drop table if EXISTS DreamkasOperation;

create table DreamkasOperation (
    id bigint not null, 
    account varchar(255), 
    amount float(53), 
    createAt datetime(6), 
    email varchar(255), 
    externalId varchar(255), 
    operationId varchar(255), 
    operationStatus enum('PENDING','IN_PROGRESS','SUCCESS','ERROR') DEFAULT NULL, 
    orderNumber varchar(255), 
    phone varchar(255), 
    primary key (id),
    key (orderNumber)
) engine = InnoDB;

drop table if EXISTS DreamkasOperation_SEQ;

create table DreamkasOperation_SEQ (next_val bigint) engine = InnoDB;

insert into DreamkasOperation_SEQ values (1);