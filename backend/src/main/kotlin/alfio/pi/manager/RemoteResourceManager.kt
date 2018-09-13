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

import alfio.pi.RemoteApiAuthenticationDescriptor
import alfio.pi.RemoteEventFilter
import alfio.pi.model.Event
import alfio.pi.model.RemoteEvent
import alfio.pi.repository.EventRepository
import alfio.pi.wrapper.doInTransaction
import alfio.pi.wrapper.tryOrDefault
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.TimeUnit


fun httpClientWithCustomTimeout(connectionTimeout: Pair<Long, TimeUnit>, readTimeout: Pair<Long, TimeUnit>? = null): (OkHttpClient) -> OkHttpClient = {
    httpClientBuilderWithCustomTimeout(connectionTimeout, readTimeout).invoke(it).build()
}

fun httpClientBuilderWithCustomTimeout(connectionTimeout: Pair<Long, TimeUnit>, readTimeout: Pair<Long, TimeUnit>? = null): (OkHttpClient) -> OkHttpClient.Builder = {
    val builder = it.newBuilder().connectTimeout(connectionTimeout.first, connectionTimeout.second)
    if(readTimeout != null) {
        builder.readTimeout(readTimeout.first, readTimeout.second)
    } else {
        builder
    }

}

@Component
@Profile("server", "full")
open class RemoteResourceManager(@Qualifier("masterConnectionConfiguration") private val configuration: RemoteApiAuthenticationDescriptor,
                                 private val httpClient: OkHttpClient,
                                 private val gson: Gson) {
    private val logger = LoggerFactory.getLogger(RemoteResourceManager::class.java)

    private fun <T> getRemoteResource(resource: String, type: TypeToken<T>, emptyResult: () -> T, timeoutMillis: Long = -1L): Pair<Boolean, T> = tryOrDefault<Pair<Boolean, T>>()
        .invoke({
            val url = "${configuration.url}$resource"
            logger.debug("Will call remote url {}", url)
            val request = Request.Builder()
                .addHeader("Authorization", configuration.authenticationHeaderValue())
                .url(url)
                .build()
            val client = if(timeoutMillis > -1L) {
                httpClientWithCustomTimeout(timeoutMillis to TimeUnit.MILLISECONDS).invoke(httpClient)
            } else {
                httpClient
            }
            client.newCall(request)
                .execute()
                .use { resp ->
                    if(resp.isSuccessful) {
                        val body = resp.body()!!.string()
                        val result: T = gson.fromJson(body, type.type)
                        true to result
                    } else {
                        false to emptyResult.invoke()
                    }
                }
        }, {
            logger.error("error while fetching remote resource $resource", it)
            false to emptyResult.invoke()
        })



    fun getRemoteEventList(): List<RemoteEvent> {
        return getRemoteResource("/admin/api/events", object : TypeToken<List<RemoteEvent>>() {}, { emptyList() }).second
    }
}



@Component
@Profile("server", "full")
open class EventSynchronizer(private val remoteResourceManager: RemoteResourceManager,
                             private val eventRepository: EventRepository,
                             private val transactionManager: PlatformTransactionManager,
                             private val jdbc: NamedParameterJdbcTemplate,
                             private val checkInDataSynchronizer: CheckInDataSynchronizer,
                             private val kvStore: KVStore,
                             private val checkInDataManager: CheckInDataManager,
                             private val eventFilter: RemoteEventFilter) {
    private val logger = LoggerFactory.getLogger(EventSynchronizer::class.java)

    @EventListener
    open fun handleContextRefresh(event: ContextRefreshedEvent) {
        sync()
    }


    @Scheduled(fixedDelay = 60L * 60000L)
    open fun sync() {
        doInTransaction<Unit>().invoke(transactionManager, {
            val localEvents = eventRepository.loadAll().filter { eventFilter.accept(it.key) }
            val remoteEvents = remoteResourceManager.getRemoteEventList().filter { eventFilter.accept(it.key!!) }
            val (existing, notExisting) = remoteEvents.partition { r -> localEvents.any { l -> r.key == l.key } }
            updateExisting(existing, localEvents)
            insertNew(notExisting)
            checkInDataSynchronizer.onDemandSync(remoteEvents)
            remoteEvents.forEach {
                loadLabelConfiguration(it.key!!)
            }
        }, {
            logger.error("cannot load events", it)
        })

    }

    private fun loadLabelConfiguration(eventName: String) {
        //fetch label config, if any
        val labelConfiguration = checkInDataManager.loadLabelConfiguration(eventName)
        eventRepository.loadSingle(eventName).ifPresent { (id) ->
            if (labelConfiguration != null) {
                kvStore.saveLabelConfiguration(id, labelConfiguration.json, labelConfiguration.enabled)
            }
        }
    }

    private fun updateExisting(remoteExisting: List<RemoteEvent>, local: List<Event>) {
        val toBeUpdated = remoteExisting.map { r ->
            val l = local.find { l -> r.key == l.key }!!
            MapSqlParameterSource().addValues(mutableMapOf(
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

    private fun insertNew(remote: List<RemoteEvent>) {
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

