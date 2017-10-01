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
import alfio.pi.model.Event
import alfio.pi.model.ScanLog
import alfio.pi.repository.*
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/internal/scan-log")
@Profile("server", "full")
open class ScanLogApi (private val scanLogRepository: KVStore,
                       private val printManager: PrintManager,
                       private val printerRepository: PrinterRepository,
                       private val labelConfigurationRepository: LabelConfigurationRepository,
                       private val userRepository: UserRepository,
                       private val environment: Environment) {

    @RequestMapping("")
    open fun loadAll(@RequestParam(value = "page", defaultValue = "0") page: Int,
                     @RequestParam(value = "pageSize", defaultValue = "3") pageSize: Int,
                     @RequestParam(value = "search", defaultValue = "") search: String) : PaginatedResult<List<ScanLog>> {
        val searchTrimmed = if (search.trim().length == 0) null else (search.trim())
        val pageAndTotalCount = scanLogRepository.loadPageAndTotalCount(page * pageSize, pageSize, searchTrimmed)
        return PaginatedResult(page, pageAndTotalCount.first, pageAndTotalCount.second)
    }

    @RequestMapping("/event/{eventKey}")
    open fun loadForEvent(@PathVariable("eventKey") eventKey: String) : List<ScanLog>  {
        return scanLogRepository.loadAllForEvent(eventKey)
    }

    @RequestMapping(value = "/event/{eventKey}/entry/{entryId}/reprint-preview", method = arrayOf(RequestMethod.GET))
    open fun getReprintPreview(@PathVariable("eventKey") eventKey: String, @PathVariable("entryId") entryId: String): ResponseEntity<ConfigurableLabelContent> =
        reprintPreview(eventKey, entryId).invoke(printManager, scanLogRepository, labelConfigurationRepository)
            .map { ResponseEntity.ok(it) }
            .orElseGet { ResponseEntity.notFound().build() }


    @RequestMapping(value = "/{entryId}/reprint", method = arrayOf(RequestMethod.PUT))
    open fun reprint(@PathVariable("entryId") entryId: String,
                     @RequestBody form: ReprintForm,
                     principal: Principal?): ResponseEntity<Boolean> {
        val printerId = form.printer
        val content = form.content
        val desk = environment.acceptsProfiles("desk")
        val username = when {
            principal != null -> principal.name
            desk -> Application.deskUsername
            else -> null
        }
        return if(username != null && (printerId != null || content != null)) {
            ResponseEntity.ok(reprintBadge(entryId, printerId, username, content, desk).invoke(printManager, printerRepository, scanLogRepository, labelConfigurationRepository, userRepository))
        } else {
            ResponseEntity(HttpStatus.BAD_REQUEST)
        }
    }
}


class ReprintForm {
    var printer: Int?=null
    var content: ConfigurableLabelContent?=null
}

data class PaginatedResult<T>(val page: Int, val values : T, val found: Int)



@RestController
@RequestMapping("/api/internal/events")
@Profile("server", "full")
open class EventApi (private val transactionManager: PlatformTransactionManager,
                     private val eventRepository: EventRepository) {

    @RequestMapping(value = "", method = arrayOf(RequestMethod.GET))
    open fun loadAll(): List<Event> = eventRepository.loadAll()

    @RequestMapping(value = "/{eventKey}", method = arrayOf(RequestMethod.GET))
    open fun getSingleEvent(@PathVariable("eventKey") eventKey: String) : ResponseEntity<Event> = eventRepository.loadSingle(eventKey)
        .map{ ResponseEntity.ok(it) }
        .orElseGet { ResponseEntity(HttpStatus.NOT_FOUND) }

    @RequestMapping(value = "/{eventKey}/active", method = arrayOf(RequestMethod.PUT, RequestMethod.DELETE))
    open fun toggleActiveState(@PathVariable("eventKey") eventKey: String, method: HttpMethod): Boolean = toggleEventActivation(eventKey, method == HttpMethod.PUT).invoke(transactionManager, eventRepository)

}