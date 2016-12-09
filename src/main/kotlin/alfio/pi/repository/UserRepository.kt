/*
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */

package alfio.pi.repository

import alfio.pi.model.Authority
import alfio.pi.model.Event
import alfio.pi.model.Role
import alfio.pi.model.User
import ch.digitalfondue.npjt.AffectedRowCountAndKey
import ch.digitalfondue.npjt.Bind
import ch.digitalfondue.npjt.Query
import ch.digitalfondue.npjt.QueryRepository
import java.util.*

@QueryRepository
interface UserRepository {

    @Query("insert into user(username, password) values(:username, :password)")
    fun insert(@Bind("username") username: String, @Bind("password") password: String): AffectedRowCountAndKey<Int>

    @Query("select * from user where username = :username")
    fun findByUsername(@Bind("username") username: String): Optional<User>

}

@QueryRepository
interface AuthorityRepository {
    @Query("insert into authority(username, role) values(:username, :role)")
    fun insert(@Bind("username") username: String, @Bind("role") role: Role): Int

    @Query("select * from authority where username = :username")
    fun findByUsername(@Bind("username") username: String): List<Authority>
}

@QueryRepository
interface EventRepository {
    @Query("select * from event")
    fun loadAll(): List<Event>

    @Query("insert into event(id, name, url) values(:eventId, :name, :url)")
    fun insert(@Bind("eventId") eventId: Int, @Bind("name") eventName: String, @Bind("url") url: String): Int
}