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

import alfio.pi.manager.SslKeyExporter
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
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.bind.annotation.*
import java.util.*
import javax.servlet.http.HttpServletResponse
import java.util.ArrayList
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO









@RestController
@RequestMapping("/api/internal/users")
@Profile("server", "full")
open class UserApi(val userRepository: UserRepository,
                   val transactionManager: PlatformTransactionManager,
                   val passwordGenerator: PasswordGenerator,
                   val passwordEncoder: PasswordEncoder,
                   val authorityRepository: AuthorityRepository,
                   @Qualifier("localServerURL") val localServerUrl: String,
                   val gson: Gson,
                   val sslKeyExporter: SslKeyExporter) {
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
                            @RequestParam("password") password: String,//base-64 encoded password
                            response: HttpServletResponse) {
        doInTransaction<Unit>().invoke(transactionManager, {
            val user = userRepository.findById(userId)
            if(user.isPresent) {
                response.status = HttpServletResponse.SC_OK
                val map = sslKeyExporter.appendTo(mapOf("baseUrl" to localServerUrl, "username" to user.get().username, "password" to String(Base64.getDecoder().decode(password))))

                val jsonString = gson.toJson(map);
                //split in 3,
                val splittedSize = jsonString.length / 3;
                val splitted = ArrayList<String>()
                splitted.add(jsonString.substring(0, splittedSize))
                splitted.add(jsonString.substring(splittedSize, 2 * splittedSize))
                splitted.add(jsonString.substring(splittedSize * 2, jsonString.length))

                //a single qrcode has a size of 350 * 350px;
                val result = BufferedImage(350, 350 * 3, BufferedImage.TYPE_INT_RGB)
                val g = result.graphics
                var i = 0
                for(payload in splitted) {
                    val idx = i+1;
                    val bi = ImageIO.read(ByteArrayInputStream(generateQRCodeImage("$idx:3:$payload")))
                    g.drawImage(bi, 0, 350 * i, null)
                    i += 1
                }
                //

                response.contentType = "image/png"
                ImageIO.write(result, "png", response.outputStream)
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

