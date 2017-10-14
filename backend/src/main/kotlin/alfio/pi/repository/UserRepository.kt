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

import alfio.pi.model.*
import ch.digitalfondue.npjt.*
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.*

@QueryRepository
interface UserRepository {

    @Query("insert into user(username, password) values(:username, :password)")
    fun insert(@Bind("username") username: String, @Bind("password") password: String): AffectedRowCountAndKey<Int>

    @Query("select id, username from user where username = :username")
    fun findByUsername(@Bind("username") username: String): Optional<User>

    @Query("select id, username from user where id = :userId")
    fun findById(@Bind("userId") id: Int): Optional<User>

    @Query("select user.id, user.username from user, authority where user.username = authority.username and authority.role <> 'ADMIN'")
    fun findAllOperators(): List<User>

    @Query("update user set password = :password where id = :userId")
    fun updatePassword(@Bind("userId") id: Int, @Bind("password") password: String)

}

@QueryRepository
interface AuthorityRepository {
    @Query("insert into authority(username, role) values(:username, :role)")
    fun insert(@Bind("username") username: String, @Bind("role") role: Role): Int
}

@QueryRepository
interface EventRepository {

    @Query("select * from event")
    fun loadAll(): List<Event>

    @Query("select * from event where key = :key")
    fun loadSingle(@Bind("key") eventName: String): Optional<Event>

    @Query(type = QueryType.TEMPLATE, value = "insert into event(key, name, image_url, begin_ts, end_ts, location, api_version, one_day, active) values(:key, :name, :imageUrl, :begin, :end, :location, :apiVersion, :oneDay, :active)")
    fun bulkInsert(): String

    @Query(type = QueryType.TEMPLATE, value = "update event set name = :name, image_url = :imageUrl, begin_ts = :begin, end_ts = :end, location = :location, api_version = :apiVersion, one_day = :oneDay where key = :key")
    fun bulkUpdate(): String

    @Query("update event set active = :state where key = :key")
    fun toggleActivation(@Bind("key") id: String, @Bind("state") state: Boolean): Int

}

@QueryRepository
interface ConfigurationRepository {

    companion object {
        const val PRINTER_REMAINING_LABEL_COUNTER = "PRINTER_REMAINING_LABEL_COUNTER"
        const val PRINTER_REMAINING_LABEL_DEFAULT_COUNTER = "PRINTER_REMAINING_LABEL_DEFAULT_COUNTER"
    }

    @Query("""merge into configuration using (values (:key, :value)) as vals(key, value) on configuration.key = vals.key
        when matched then update set configuration.value = vals.value
        when not matched then insert values vals.key, vals.value""")
    fun insertOrUpdate(@Bind("key") key : String, @Bind("value") value: String) : Int

    @Query("select value from configuration where key = :key")
    fun getData(@Bind("key") key : String) : Optional<String>
}