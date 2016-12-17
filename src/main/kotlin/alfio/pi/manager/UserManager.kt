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

import alfio.pi.model.Role
import alfio.pi.model.User
import alfio.pi.model.UserWithPassword
import alfio.pi.repository.AuthorityRepository
import alfio.pi.repository.UserRepository
import alfio.pi.util.PasswordGenerator
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder

private val logger = LoggerFactory.getLogger("UserManager")!!

fun updatePassword(user: User): (PasswordGenerator, PasswordEncoder, UserRepository) -> UserWithPassword = { generator, encoder, userRepository ->
    val newPassword = generator.generateRandomPassword()
    userRepository.updatePassword(user.id, encoder.encode(newPassword))
    UserWithPassword(user.id, user.username, newPassword)
}

fun createNewUser(username: String): (PasswordGenerator, PasswordEncoder, UserRepository, AuthorityRepository) -> UserWithPassword = { generator, encoder, userRepository, authorityRepository ->
    val password = generator.generateRandomPassword()
    val userResult = userRepository.insert(username, password)
    authorityRepository.insert(username, Role.OPERATOR)
    UserWithPassword(userResult.key, username, password)
}

