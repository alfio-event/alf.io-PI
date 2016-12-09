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

package alfio.pi.util

import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.util.*
import java.util.regex.Pattern

@Component
open class PasswordGenerator(environment: Environment) {

    private val PASSWORD_CHARACTERS: CharArray
    private val DEV_MODE: Boolean = environment.acceptsProfiles("dev")
    private val MAX_LENGTH = 14
    private val MIN_LENGTH = 10
    private val VALIDATION_PATTERN: Pattern

    init {
        val chars = LinkedList<Char>()
        chars += 'a' .. 'z'
        chars += 'A' .. 'Z'
        chars += '0' .. '9'
        chars += arrayOf('#','~','!', '-', '_', '/', '^', '&', '+', '%', '(',')','=')

        PASSWORD_CHARACTERS = chars.toCharArray()
        VALIDATION_PATTERN = Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*\\p{Punct})(?=\\S+$).{$MIN_LENGTH,}$")//source: http://stackoverflow.com/a/3802238
    }

    fun generateRandomPassword(): String {
        if (DEV_MODE) {
            return "abcd"
        }
        val length = MIN_LENGTH + Knuth.gen.nextInt(MAX_LENGTH - MIN_LENGTH + 1)
        return PASSWORD_CHARACTERS.toTypedArray().shuffle().take(length).joinToString(separator = "")
    }

    fun isValid(password: String?): Boolean {
        return password != null && password.isNotBlank() && VALIDATION_PATTERN.matcher(password).matches()
    }
}

/**
 * Knuth shuffle (a.k.a. the Fisher-Yates shuffle)
 * source https://rosettacode.org/wiki/Knuth_shuffle#Kotlin
 */
object Knuth {
    internal val gen = java.util.Random()
}

fun <T> Array<T>.shuffle(): Array<T> {
    val a = clone()
    var n = a.size
    while (n > 1) {
        val k = Knuth.gen.nextInt(n--)
        val t = a[n]
        a[n] = a[k]
        a[k] = t
    }
    return a
}