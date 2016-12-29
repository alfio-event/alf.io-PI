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

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test

import org.junit.Assert.*
import sun.security.provider.X509Factory
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.*
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class CheckInDataManagerTest {
    @Test
    fun testCalcHash256() {
        assertEquals("2e99758548972a8e8822ad47fa1017ff72f06f3ff6a016851f45c398732bc50c", calcHash256("this is a test"))
    }

    /*
    @Test
    fun testLoadTrustManager() {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        val password = "password".toCharArray()
        keyStore.load(null, password)
        val body = "MIIB/DCCAWUCCCe9iHfkD4q0MA0GCSqGSIb3DQEBCwUAMEMxEjAQBgNVBAMMCWxv\nY2FsaG9zdDENMAsGA1UECwwETm9uZTEPMA0GA1UECgwGTm9uZSBMMQ0wCwYDVQQG\nEwROb25lMB4XDTE2MTIyODE0MzM0NFoXDTE3MTIyODE0MzM0NFowQzESMBAGA1UE\nAwwJbG9jYWxob3N0MQ0wCwYDVQQLDAROb25lMQ8wDQYDVQQKDAZOb25lIEwxDTAL\nBgNVBAYTBE5vbmUwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAKvuDmw4ZUGB\nE55SMdvrxbR1y70yyW7DBbZcRf+sznIWFvzY6qSywDUeuMYfQVnjSBPJj5sCqWoD\nRiI39yyxBlsZ9EocnUfbWXUFynL1ioPasZtpKLo7TJx0KGphwTKDpOyZWHuTwQRp\n2cAkWgRxEJXPB6wBIMMUEFOKdEFc133HAgMBAAEwDQYJKoZIhvcNAQELBQADgYEA\ndcZUQA0thM38D1dJx9OSpMDGWcdnWIWQ/WwOrdlmV3UfCGPPeYkI1OOujYiQNypK\nAS0wyiZ7gBCCymQS2olTqyOcVYD8D9q+5zcp17v5B/Ktri4VNMXpFtzWRU45QeZi\nKKvA+fdl5TSIrrCdbZihtyxTJdeirLbCzwITh+uVLgg="
        val certificate = "${X509Factory.BEGIN_CERT}\n$body\n${X509Factory.END_CERT}"
        keyStore.setCertificateEntry("192.168.1.4", CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(certificate.toByteArray())))
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, password)
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)
        val trustManager = trustManagerFactory.trustManagers[0] as X509TrustManager
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)

        val client = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .build()
        val request = Request.Builder()
            .url("https://localhost:8443")
            .build()
        client.newCall(request).execute()
    }
    */

}