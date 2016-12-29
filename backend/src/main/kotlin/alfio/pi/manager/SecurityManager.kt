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

import alfio.pi.Constants
import alfio.pi.Constants.KEYSTORE_FILE
import alfio.pi.Constants.KEYSTORE_PASS
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyStore
import java.util.*

interface SslKeyExporter {
    fun appendTo(map: Map<String, String>): Map<String, String>
}

@Component
@Profile("!dev")
open class LiveSslKeyExporter : SslKeyExporter {

    val certificate: String

    init {
        val keyStore = KeyStore.getInstance("JKS")
        keyStore.load(Files.newInputStream(Paths.get(KEYSTORE_FILE.value)), KEYSTORE_PASS.value.toCharArray())
        certificate = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keyStore.getCertificate(Constants.KEY_ALIAS.value).encoded)
    }

    override fun appendTo(map: Map<String, String>) = map.plus("sslCert" to certificate)

}

@Component
@Profile("dev")
open class FakeSslKeyExporter : SslKeyExporter {
    //does nothing
    override fun appendTo(map: Map<String, String>): Map<String, String> = map
}

