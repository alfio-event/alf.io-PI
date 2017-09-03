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

import alfio.pi.isLocalAddress
import alfio.pi.repository.ConfigurationRepository
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/internal/system")
@Profile("desk")
open class SystemApi(private val configurationRepository: ConfigurationRepository) {
    @RequestMapping(value = "/power-off", method = arrayOf(RequestMethod.PUT))
    open fun powerOff(servletRequest: HttpServletRequest): ResponseEntity<String> {
        if(!isLocalAddress(servletRequest.remoteAddr)) {
            return ResponseEntity(HttpStatus.NOT_MODIFIED)
        }
        val result = Runtime.getRuntime().exec("sudo poweroff").waitFor()
        return if(result == 0) {
            ResponseEntity.ok("shutting down...")
        } else {
            ResponseEntity(HttpStatus.NOT_MODIFIED)
        }
    }

    @RequestMapping(value = "configuration/{key}", method = arrayOf(RequestMethod.POST))
    open fun insertOrUpdateConfiguration(@PathVariable("key") key : String, @RequestBody value : String) {
        configurationRepository.insertOrUpdate(key, value)
    }

    @RequestMapping(value = "configuration/{key}", method = arrayOf(RequestMethod.GET))
    open fun getConfigurationValue(@PathVariable("key") key : String) : String? {
        return configurationRepository.getData(key).orElse(null)
    }
}