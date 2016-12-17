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

import alfio.pi.manager.createNewUser
import alfio.pi.manager.generateQRCodeImage
import alfio.pi.manager.updatePassword
import alfio.pi.model.User
import alfio.pi.model.UserWithPassword
import alfio.pi.repository.AuthorityRepository
import alfio.pi.repository.UserRepository
import alfio.pi.util.PasswordGenerator
import alfio.pi.wrapper.doInTransaction
import com.google.gson.Gson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletResponse

@RestController
@RequestMapping("/api/users")
open class UserApi(val userRepository: UserRepository,
                   val transactionManager: PlatformTransactionManager,
                   val passwordGenerator: PasswordGenerator,
                   val passwordEncoder: PasswordEncoder,
                   val authorityRepository: AuthorityRepository,
                   @Qualifier("localServerURL") val localServerUrl: String,
                   val gson: Gson) {
    private val logger = LoggerFactory.getLogger(UserApi::class.java)

    @RequestMapping(value = "", method = arrayOf(RequestMethod.GET))
    open fun loadAllOperators(): List<User> = doInTransaction<List<User>>().invoke(transactionManager, {userRepository.findAllOperators()}, {
        logger.error("error while loading users", it)
        emptyList()
    })

    @RequestMapping(value = "/{userId}", method = arrayOf(RequestMethod.GET))
    open fun loadSingleUser(@PathVariable("userId") userId: Int): ResponseEntity<User> = doInTransaction<ResponseEntity<User>>().invoke(transactionManager, {
        userRepository.findById(userId).map {
            ResponseEntity.ok(it)
        }.orElseGet(fun(): ResponseEntity<User> {
            return ResponseEntity(HttpStatus.NOT_FOUND)
        })
    }, {
        logger.error("error while loading users", it)
        ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR)
    })

    @RequestMapping(value = "/", method = arrayOf(RequestMethod.POST))
    open fun create(@RequestBody() form: UserForm): ResponseEntity<UserWithPassword> = doInTransaction<ResponseEntity<UserWithPassword>>().invoke(transactionManager, {
        val username = form.username
        if(username != null && username.isNotBlank()) {
            ResponseEntity.ok(createNewUser(username).invoke(passwordGenerator, passwordEncoder, userRepository, authorityRepository))
        } else {
            ResponseEntity(HttpStatus.BAD_REQUEST)
        }
    }, {
        logger.error("error while loading users", it)
        ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR)
    })

    @RequestMapping(value = "/{userId}/resetPassword", method = arrayOf(RequestMethod.POST))
    open fun resetPassword(@PathVariable("userId") userId: Int): ResponseEntity<UserWithPassword> = doInTransaction<ResponseEntity<UserWithPassword>>().invoke(transactionManager, {
        userRepository.findById(userId).map {
            ResponseEntity.ok(updatePassword(it).invoke(passwordGenerator, passwordEncoder, userRepository))
        }.orElseGet(fun(): ResponseEntity<UserWithPassword> {
            return ResponseEntity(HttpStatus.NOT_FOUND)
        })
    }, {
        logger.error("error while resetting password", it)
        ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR)
    })


    @RequestMapping(value = "/{userId}/qr-code", method = arrayOf(RequestMethod.GET))
    open fun generateQRCode(@PathVariable("userId") userId: Int,
                            @RequestParam("password") password: String,
                            response: HttpServletResponse) {
        doInTransaction<Unit>().invoke(transactionManager, {
            val user = userRepository.findById(userId)
            if(user.isPresent) {
                response.status = HttpServletResponse.SC_OK
                response.outputStream.write(generateQRCodeImage(gson.toJson(mapOf("baseUrl" to localServerUrl, "username" to user.get().username, "password" to password))))
            }
            response.status = HttpServletResponse.SC_NOT_FOUND
        }, {
            logger.error("error while generating QR code", it)
            response.status = HttpServletResponse.SC_NOT_FOUND
        })

    }

    class UserForm {
        var username: String? = null
    }
}
