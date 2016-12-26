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

import alfio.pi.manager.*
import alfio.pi.model.CheckInResponse
import alfio.pi.model.Event
import alfio.pi.repository.EventRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/admin/api/check-in")
open class CheckInApi(val checkInDataManager: CheckInDataManager) {

    @RequestMapping(value = "/event/{eventName}/ticket/{ticketIdentifier}", method = arrayOf(RequestMethod.POST))
    open fun performCheckIn(@PathVariable("eventName") eventName: String,
                            @PathVariable("ticketIdentifier") ticketIdentifier: String,
                            @RequestBody ticketCode: TicketCode,
                            principal: Principal): CheckInResponse {
        return checkIn(eventName, ticketIdentifier, (ticketCode.code!!).substringAfter('/'), principal.name).invoke(checkInDataManager)
    }

    class TicketCode {
        var code: String? = null
    }
}

@RestController
open class AppEventApi(val eventRepository: EventRepository) {

    @RequestMapping(value = "/api/events/{eventName}", method = arrayOf(RequestMethod.GET))
    open fun loadPublicEvent(@PathVariable("eventName") eventName: String): ResponseEntity<Event> = findLocalEvent(eventName).invoke(eventRepository).map {
        ResponseEntity.ok(it)
    }.orElseGet {
        ResponseEntity(HttpStatus.NOT_FOUND)
    }

    @RequestMapping(value = "/admin/api/events", method = arrayOf(RequestMethod.GET))
    open fun loadEvents() = findLocalEvents().invoke(eventRepository)

    @RequestMapping(value = "/admin/api/user-type", method = arrayOf(RequestMethod.GET))
    open fun loadUserType() = "STAFF"//Sponsors should call the central server
}

@Controller
@RequestMapping("/file")
open class ResourceController() {
    @RequestMapping("/*")
    open fun loadImage() = "redirect:/images/logo-alfio-pi.png"
}