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

package alfio.pi.controller

import alfio.pi.Application
import alfio.pi.manager.*
import alfio.pi.model.CheckInResponse
import alfio.pi.model.Event
import alfio.pi.repository.EventRepository
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.util.*

@RestController
@Profile("server", "full")
@RequestMapping("/admin/api/check-in")
class CheckInApi(private val checkInDataManager: CheckInDataManager,
                 private val badgeScanManager: BadgeScanManager,
                 private val environment: Environment) {

    @RequestMapping(value = ["/event/{eventName}/ticket/{ticketIdentifier:.+}"], method = [(RequestMethod.POST)])
    fun performCheckIn(@PathVariable("eventName") eventName: String,
                       @PathVariable("ticketIdentifier") ticketIdentifier: String,
                       @RequestBody ticketCode: TicketCode,
                       principal: Principal?): ResponseEntity<CheckInResponse> {

        val username = if((principal == null) and environment.acceptsProfiles(Profiles.of("desk"))) Application.deskUsername else principal?.name
        logger.info("ticket {}: received code {}", ticketIdentifier, ticketCode.code)
        return Optional.ofNullable(username)
            .map {
                val code = ticketCode.code
                if(code == null || code.indexOf('/') == -1) {
                    ResponseEntity.ok(badgeScanManager.performBadgeScan(eventName, ticketIdentifier, it))
                } else {
                    ResponseEntity.ok(checkInDataManager.performCheckIn(eventName, ticketIdentifier, code.substringAfter('/'), it))
                }
            }.orElseGet {
                ResponseEntity(HttpStatus.UNAUTHORIZED)
            }
    }

    @RequestMapping(value = ["/event/{eventName}/force-print-label-ticket/{ticketIdentifier:.+}"], method = [(RequestMethod.POST)])
    fun printLabel(@PathVariable("eventName") eventName: String,
                   @PathVariable("ticketIdentifier") ticketIdentifier: String,
                   @RequestBody ticketCode: TicketCode,
                   principal: Principal?) : ResponseEntity<Boolean> {
        val username = if((principal == null) and environment.acceptsProfiles(Profiles.of("desk"))) Application.deskUsername else principal?.name
        return Optional.ofNullable(username)
            .map {
                ResponseEntity.ok(checkInDataManager.forcePrintLabel(eventName, ticketIdentifier, (ticketCode.code!!).substringAfter('/'), it!!))
            }.orElseGet {
                ResponseEntity(HttpStatus.UNAUTHORIZED)
            }
    }

    class TicketCode {
        var code: String? = null
    }
}

@RestController
@Profile("server", "full")
class AppEventApi(private val eventRepository: EventRepository) {

    @RequestMapping(value = ["/api/events/{eventName}"], method = [(RequestMethod.GET)])
    fun loadPublicEvent(@PathVariable("eventName") eventName: String): ResponseEntity<Event> {
        return eventRepository.loadSingle(eventName).map {
            ResponseEntity.ok(it)
        }.orElseGet {
            ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    @RequestMapping(value = ["/admin/api/events"], method = [(RequestMethod.GET)])
    fun loadEvents() = eventRepository.loadAll().filter { it.active }

    @RequestMapping(value = ["/admin/api/user-type"], method = [(RequestMethod.GET)])
    fun loadUserType() = "STAFF"//Sponsors should call the central server
}

@Controller
@Profile("server", "full")
@RequestMapping("/file")
class ResourceController {
    @RequestMapping("/*")
    fun loadImage() = "redirect:/images/logo-alfio-pi.png"
}