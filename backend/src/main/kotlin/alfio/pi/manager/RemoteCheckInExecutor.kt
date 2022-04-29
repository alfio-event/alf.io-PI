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
import alfio.pi.model.ScanLog
import alfio.pi.model.TicketAndCheckInResult
import alfio.pi.repository.UserRepository
import alfio.pi.wrapper.tryOrDefault
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
open class RemoteCheckInExecutor(private val gson: Gson,
                            @Qualifier("masterConnectionConfiguration") private val master: RemoteApiAuthenticationDescriptor,
                            private val httpClient: OkHttpClient,
                            @Value("\${checkIn.forcePaymentOnSite:false}") private val checkInForcePaymentOnSite: Boolean,
                            private val userRepository: UserRepository) {

    private val logger = LoggerFactory.getLogger(RemoteCheckInExecutor::class.java)

    open fun remoteBulkCheckIn(eventKey: String, scanLogEntries: List<ScanLog>, identifierTransformer: (List<ScanLog>) -> List<Map<String, String?>>) : Map<String, TicketAndCheckInResult> {
        return tryOrDefault<Map<String, TicketAndCheckInResult>>().invoke({
            val username = userRepository.findById(scanLogEntries.first { userRepository.findById(it.userId).isPresent }.userId).get().username
            val identifierCodes = identifierTransformer.invoke(scanLogEntries)
            val requestBody = gson.toJson(identifierCodes).toRequestBody("application/json".toMediaTypeOrNull())
            var url = "${master.url}/admin/api/check-in/event/$eventKey/bulk?offlineUser=$username"
            if(checkInForcePaymentOnSite) {
                url += "&forceCheckInPaymentOnSite=true"
            }
            val request = Request.Builder()
                .addHeader("Authorization", master.authenticationHeaderValue())
                .post(requestBody)
                .url(url)
                .build()
            logger.debug("Will call remote url {}", url)
            httpClientWithCustomTimeout(1L to TimeUnit.SECONDS, 10L to TimeUnit.SECONDS)
                .invoke(httpClient)
                .newCall(request)
                .execute()
                .use { resp ->
                    if (resp.isSuccessful) {
                        gson.fromJson(resp.body!!.string(), object : TypeToken<Map<String, TicketAndCheckInResult>>() {}.type)
                    } else {
                        emptyMap()
                    }
                }
        }, {
            logger.warn("got Exception while performing remote check-in ($it)")
            emptyMap()
        })
    }
}