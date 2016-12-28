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

package alfio.pi.manager

import alfio.pi.ConnectionDescriptor
import alfio.pi.model.Event
import alfio.pi.model.RemoteEvent
import alfio.pi.repository.EventRepository
import alfio.pi.wrapper.doInTransaction
import alfio.pi.wrapper.tryOrDefault
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.TimeUnit

val httpClient = OkHttpClient()

fun httpClientWithCustomTimeout(timeout: Long, timeUnit: TimeUnit): OkHttpClient = httpClient
    .newBuilder()
    .connectTimeout(timeout, timeUnit)
    .build()

@Component
open class RemoteResourceManager(@Qualifier("masterConnectionConfiguration") val configuration: ConnectionDescriptor,
                                 val gson: Gson) {
    private val logger = LoggerFactory.getLogger(RemoteResourceManager::class.java)

    internal fun <T> getRemoteResource(resource: String, type: TypeToken<T>, emptyResult: () -> T, timeoutMillis: Long = -1L): Pair<Boolean, T> = tryOrDefault<Pair<Boolean, T>>()
        .invoke({
            val request = Request.Builder()
                .addHeader("Authorization", Credentials.basic(configuration.username, configuration.password))
                .url("${configuration.url}$resource")
                .build()
            val client = if(timeoutMillis > -1L) {
                httpClientWithCustomTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            } else {
                httpClient
            }
            val resp = client.newCall(request).execute()
            if(resp.isSuccessful) {
                resp.body().use {
                    val body = it.string()
                    val result: T = gson.fromJson(body, type.type)
                    true to result
                }
            } else {
                false to emptyResult.invoke()
            }
        }, {
            logger.error("error while fetching remote resource $resource", it)
            false to emptyResult.invoke()
        })
}

fun getRemoteEventList(): (RemoteResourceManager) -> List<RemoteEvent> = {
    it.getRemoteResource("/admin/api/events", object : TypeToken<List<RemoteEvent>>() {}, { emptyList() }).second
}

@Component
open class EventSynchronizer(val remoteResourceManager: RemoteResourceManager,
                             val eventRepository: EventRepository,
                             val transactionManager: PlatformTransactionManager,
                             val jdbc: NamedParameterJdbcTemplate,
                             val checkInDataSynchronizer: CheckInDataSynchronizer) {
    private val logger = LoggerFactory.getLogger(EventSynchronizer::class.java)
    @Scheduled(fixedDelay = 60L * 60000L)
    open fun sync() {
        doInTransaction<Unit>().invoke(transactionManager, {
            val localEvents = eventRepository.loadAll()
            val remoteEvents = getRemoteEventList().invoke(remoteResourceManager)
            val (existing, notExisting) = remoteEvents.partition { r -> localEvents.any { l -> r.key == l.key } }
            updateExisting(existing, localEvents).invoke(jdbc, eventRepository)
            insertNew(notExisting).invoke(jdbc, eventRepository)
            checkInDataSynchronizer.onDemandSync(remoteEvents)
        }, {
            logger.error("cannot load events", it)
        })

    }

    private fun updateExisting(remoteExisting: List<RemoteEvent>, local: List<Event>): (NamedParameterJdbcTemplate, EventRepository) -> Unit = {jdbc, eventRepository ->
        val toBeUpdated = remoteExisting.map { r ->
            val l = local.find { l -> r.key == l.key }!!
            MapSqlParameterSource().addValues(mutableMapOf("id" to l.id,
                "key" to r.key,
                "name" to r.name,
                "imageUrl" to r.imageUrl,
                "begin" to Date.from(ZonedDateTime.parse(r.begin).toInstant()),
                "end" to Date.from(ZonedDateTime.parse(r.end).toInstant()),
                "location" to r.location,
                "apiVersion" to r.apiVersion,
                "oneDay" to r.oneDay,
                "active" to l.active))
        }.toTypedArray()
        if(!toBeUpdated.isEmpty()) {
            jdbc.batchUpdate(eventRepository.bulkUpdate(), toBeUpdated)
        }
    }

    private fun insertNew(remote: List<RemoteEvent>): (NamedParameterJdbcTemplate, EventRepository) -> Unit = {jdbc, eventRepository ->
        val toBeCreated = remote.map { r ->
            MapSqlParameterSource().addValues(mutableMapOf(
                "key" to r.key,
                "name" to r.name,
                "imageUrl" to r.imageUrl,

                "begin" to Date.from(ZonedDateTime.parse(r.begin).toInstant()),
                "end" to Date.from(ZonedDateTime.parse(r.end).toInstant()),
                "location" to r.location,
                "apiVersion" to r.apiVersion,
                "oneDay" to r.oneDay,
                "active" to true))
        }.toTypedArray()
        if(!toBeCreated.isEmpty()) {
            jdbc.batchUpdate(eventRepository.bulkInsert(), toBeCreated)
        }
    }

}

