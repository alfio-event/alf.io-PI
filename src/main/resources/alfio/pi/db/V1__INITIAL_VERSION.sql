--
-- This file is part of alf.io.
--
-- alf.io is free software: you can redistribute it and/or modify
-- it under the terms of the GNU General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- alf.io is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU General Public License for more details.
--
-- You should have received a copy of the GNU General Public License
-- along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
--

--entities
create table event (
    id integer not null,
    name varchar(255) not null
);

alter table event add constraint "pk_event_id" primary key (id);

create table printer (
    id integer identity not null,
    name varchar(1024) not null,
    description varchar(2048)
);

create table check_in_queue (
    id integer identity not null,
    event_id integer not null,
    name varchar(255) not null,
    description varchar(2048),
    printer_id_fk integer
);

alter table check_in_queue add foreign key(printer_id_fk) references printer(id);

create table scan_log (
    id integer identity not null,
    event_id integer not null,
    queue_id_fk integer not null,
    ticket_uuid varchar(255) not null,
    user varchar(255) not null,
    local_result varchar(255) not null,
    remote_result varchar(255) not null,
    badge_printed boolean default false not null
);

alter table scan_log add foreign key(queue_id_fk) references check_in_queue(id);

create table user (
    id integer identity not null,
    username varchar(255) not null,
    password varchar(2048) not null
);

create table user_queue (
    user_id_fk integer not null,
    event_id_fk integer not null,
    queue_id_fk integer not null
);

alter table user_queue add foreign key(user_id_fk) references user(id);
alter table user_queue add foreign key(event_id_fk) references event(id);
alter table user_queue add foreign key(queue_id_fk) references check_in_queue(id);
alter table user_queue add constraint "unique_user_queue_event" unique(user_id_fk, event_id_fk, queue_id_fk);


