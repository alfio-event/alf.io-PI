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
create cached table event (
    key varchar(2048) not null PRIMARY KEY ,
    name varchar(2048) not null,
    image_url varchar(2048),
    begin_ts DATETIME not null,
    end_ts DATETIME not null,
    location VARCHAR(2048),
    one_day boolean default false not null,
    api_version integer not null,
    active boolean default false not null,
    last_update DATETIME
);

create cached table printer (
    id integer identity not null,
    name varchar(1024) not null,
    description varchar(2048),
    active boolean default false not null
);

create cached table user (
    id integer identity not null,
    username varchar(255) not null,
    password varchar(2048) not null
);

create cached table authority(
    username varchar(255) not null,
    role varchar(255) not null
);

create cached table user_printer (
    user_id_fk integer not null,
    printer_id_fk integer not null
);

alter table user_printer add foreign key(user_id_fk) references user(id);
alter table user_printer add foreign key(printer_id_fk) references printer(id);
alter table user_printer add constraint "unique_user" unique(user_id_fk);

create cached table configuration (
    key varchar(256),
    value clob
);

alter table configuration add primary key(key);