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
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component
import java.util.*
import java.util.regex.Pattern

@Component
class PasswordGenerator(environment: Environment) {

    private final val passwordCharacters: CharArray
    private final val devMode: Boolean = environment.acceptsProfiles(Profiles.of("dev"))
    private final val maxLength = 14
    private final val minLength = 10
    private final val validationPattern: Pattern

    init {
        val chars = LinkedList<Char>()
        chars += 'a' .. 'z'
        chars += 'A' .. 'Z'
        chars += '0' .. '9'
        chars += arrayOf('#','~','!', '-', '_', '/', '^', '&', '+', '%', '(',')','=')

        passwordCharacters = chars.toCharArray()
        validationPattern = Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*\\p{Punct})(?=\\S+$).{$minLength,}$")//source: http://stackoverflow.com/a/3802238
    }

    fun generateRandomPassword(): String {
        if (devMode) {
            return "abcd"
        }
        val length = minLength + Knuth.gen.nextInt(maxLength - minLength + 1)
        return passwordCharacters.toTypedArray().shuffle().take(length).joinToString(separator = "")
    }

    fun isValid(password: String?): Boolean {
        return password != null && password.isNotBlank() && validationPattern.matcher(password).matches()
    }
}

/**
 * Knuth shuffle (a.k.a. the Fisher-Yates shuffle)
 * source https://rosettacode.org/wiki/Knuth_shuffle#Kotlin
 */
object Knuth {
    internal val gen = Random()
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