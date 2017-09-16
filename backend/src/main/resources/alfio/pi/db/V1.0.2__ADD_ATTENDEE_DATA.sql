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


create cached table attendee_data (
    event varchar(2048) not null,
    identifier varchar(2048) not null,
    data clob not null,
    last_update BIGINT
);

alter table attendee_data add PRIMARY key(event, identifier);

create index attendee_data_identifier_idx on attendee_data(identifier);

drop table event_data;