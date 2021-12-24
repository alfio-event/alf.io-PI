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
package alfio.pi

import alfio.pi.Constants.*
import alfio.pi.manager.SystemEventHandlerImpl
import alfio.pi.model.Role
import alfio.pi.model.Ticket
import alfio.pi.repository.AuthorityRepository
import alfio.pi.repository.UserRepository
import alfio.pi.util.PasswordGenerator
import alfio.pi.wrapper.tryOrDefault
import ch.digitalfondue.npjt.EnableNpjt
import ch.digitalfondue.npjt.mapper.ColumnMapperFactory
import ch.digitalfondue.npjt.mapper.ParameterConverter
import ch.digitalfondue.npjt.mapper.ZonedDateTimeMapper
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.zaxxer.hikari.HikariDataSource
import okhttp3.Credentials
import okhttp3.OkHttpClient
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v1CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.core.annotation.Order
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.http.HttpMethod
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.util.ClassUtils
import org.springframework.util.MethodInvoker
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import java.io.IOException
import java.math.BigInteger
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.SecureRandom
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.*
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import javax.sql.DataSource

private val logger = LoggerFactory.getLogger(Application::class.java)!!

@SpringBootApplication
@EnableTransactionManagement
@EnableScheduling
@EnableNpjt(basePackages = ["alfio.pi.repository"])
class Application {


    @Bean
    @Profile("server", "full")
    fun dataSource(@Qualifier("databaseConfiguration") config: ConnectionDescriptor) : DataSource {
        val dataSource = HikariDataSource()
        dataSource.jdbcUrl = config.url
        dataSource.username = config.username
        dataSource.password = config.password
        return dataSource
    }

    @Bean
    @Profile("server", "full")
    fun getPasswordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    @Profile("server", "full")
    fun namedParameterJdbcTemplate(dataSource: DataSource): NamedParameterJdbcTemplate = NamedParameterJdbcTemplate(dataSource)

    @Bean
    @Profile("server", "full")
    fun additionalColumnMappers(): List<ColumnMapperFactory> {
        return listOf(ZonedDateTimeMapper.Factory())
    }

    @Bean
    @Profile("server", "full")
    fun additionalParameterConverters(): List<ParameterConverter> {
        return listOf(ZonedDateTimeMapper.Converter())
    }

    @Bean
    @Profile("server", "full")
    fun databaseConfiguration(@Value("\${jdbc.url}") url: String,
                              @Value("\${jdbc.username}") username: String,
                              @Value("\${jdbc.password}") password: String): ConnectionDescriptor = ConnectionDescriptor(url, username, password)

    @Bean
    fun masterConnectionConfiguration(@Value("\${master.url}") url: String,
                                      @Value("\${master.username:#{null}}") username: String?,
                                      @Value("\${master.password:#{null}}") password: String?,
                                      @Value("\${master.apiKey:#{null}}") apiKey: String?): RemoteApiAuthenticationDescriptor = RemoteApiAuthenticationDescriptor(url, username, password, apiKey)

    @Bean
    fun remoteEventsFilter(@Value("\${events.filter:#{null}}") eventNames: String?) = RemoteEventFilter(eventNames.orEmpty())



    private val checkInColorPrefix = "checkIn.color.category."

    @Bean
    fun categoryColorConfiguration(@Value("\${checkIn.color.default:#{null}}") defaultColor: String?, environment: ConfigurableEnvironment): CategoryColorConfiguration {
        val customColors: Map<String, String> = environment.propertySources.asSequence()
            .filter { it is EnumerablePropertySource }
            .flatMap { (it as EnumerablePropertySource).propertyNames.asSequence() }
            .filter { it.startsWith(checkInColorPrefix) }
            .associate { it.substring(checkInColorPrefix.length) to (environment.getProperty(it) ?: "") }
        return CategoryColorConfiguration(defaultColor ?: "success", customColors)
    }


    @Bean
    @Profile("server", "full")
    fun localServerURL(env: Environment): String {
        val scheme = if(env.acceptsProfiles(Profiles.of("dev"))) {
            "http"
        } else {
            "https"
        }
        val port = env.getProperty("server.port", "8080")
        val address = env.getProperty("alfio.server.address")
        return "$scheme://$address:$port"
    }

    @Bean
    fun gson(): Gson {
        val builder = GsonBuilder()
        builder.registerTypeAdapter(ZonedDateTime::class.java, getZonedDateTimeSerializer())
        return builder.create()
    }

    @Bean
    fun httpClient(): OkHttpClient = OkHttpClient()

    @Bean
    @Profile("server", "printer", "full")
    fun trustManager(): X509TrustManager {
        val keyStore = KeyStore.getInstance("JKS")
        keyStore.load(Files.newInputStream(Paths.get(KEYSTORE_FILE.value)), KEYSTORE_PASS.value.toCharArray())
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)
        return trustManagerFactory.trustManagers[0] as X509TrustManager
    }

    private fun getZonedDateTimeSerializer(): JsonSerializer<ZonedDateTime> {
        val timeFormatter = DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .toFormatter(Locale.ROOT)
        return JsonSerializer { src, _, _ -> JsonPrimitive(src.withZoneSameInstant(ZoneId.of("UTC")).format(DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral('T')
            .append(timeFormatter)
            .appendLiteral('Z')
            .toFormatter(Locale.ROOT)))
        }
    }

    @Bean
    @Profile("server", "full")
    fun initializer() = ApplicationListener<ContextRefreshedEvent> {
        val applicationContext = it.applicationContext
        val user = applicationContext.getBean(UserRepository::class.java).findByUsername("admin")
        if(!user.isPresent) {
            val password = applicationContext.getBean(PasswordGenerator::class.java).generateRandomPassword()
            val encryptedPassword = applicationContext.getBean(PasswordEncoder::class.java).encode(password)
            applicationContext.getBean(UserRepository::class.java).insert("admin", encryptedPassword)
            applicationContext.getBean(AuthorityRepository::class.java).insert("admin", Role.ADMIN)
            logger.info("*******************************************************************")
            logger.info("# You are running alf.io for the first time                       ")
            logger.info("# Please use the following credentials to access the application: ")
            logger.info("#                     username: admin                             ")
            logger.info("#                     password: $password                         ")
            logger.info("*******************************************************************")
        }
    }

    @Bean
    @Profile("desk")
    fun initializeDeskUser() = ApplicationListener<ContextRefreshedEvent> {
        val applicationContext = it.applicationContext
        val user = applicationContext.getBean(UserRepository::class.java).findByUsername(deskUsername)
        if(!user.isPresent) {
            val password = applicationContext.getBean(PasswordGenerator::class.java).generateRandomPassword()
            val encryptedPassword = applicationContext.getBean(PasswordEncoder::class.java).encode(password)
            applicationContext.getBean(UserRepository::class.java).insert(deskUsername, encryptedPassword)
            applicationContext.getBean(AuthorityRepository::class.java).insert(deskUsername, Role.OPERATOR)
            logger.info("desk user created")
        }
    }

    @Bean
    @Profile("server", "full")
    fun initializerForExposingServerUrl(env: Environment) = ApplicationListener<ContextRefreshedEvent> {
        try {
            val jmdns = JmDNS.create(InetAddress.getLocalHost())
            val port = env.getProperty("server.port", Int::class.java, 8080)
            val serviceInfo = ServiceInfo.create("_http._tcp.local.", "alfio-server", port, "url="+localServerURL(env))
            logger.info("Exposing service through mdns")
            jmdns.registerService(serviceInfo)
        } catch (e: IOException) {
            logger.info("Error while exposing the service through mdns", e)
        }
    }

    companion object {
        const val deskUsername = "desk-user"
    }
}

@EnableWebSecurity
abstract class WebSecurityConfig : WebSecurityConfigurerAdapter() {

    @Autowired
    fun authenticationManager(auth: AuthenticationManagerBuilder, passwordEncoder: PasswordEncoder, dataSource: DataSource) {
        auth.jdbcAuthentication()
            .dataSource(dataSource)
            .usersByUsernameQuery("select username, password, true from user where username = ?")
            .authoritiesByUsernameQuery("select username, 'ROLE_' || role from authority where username = ?")
            .passwordEncoder(passwordEncoder)
    }
}
@Configuration
@Profile("server", "full")
@Order(0)
class PrintApiSecurity: WebSecurityConfigurerAdapter() {
    override fun configure(http: HttpSecurity) {
        http.requestMatcher { it.requestURI == "/api/printers/register" }
            .csrf().disable()
            .authorizeRequests()
            .antMatchers(HttpMethod.POST, "/api/printers/register").permitAll()
    }
}

@Configuration
@Profile("desk")
@Order(1)
class DeskWebSecurity : WebSecurityConfigurerAdapter() {
    override fun configure(http: HttpSecurity) {
        http.requestMatcher { isLocalAddress(it.remoteAddr) }
            .anonymous()
            .authorities("ROLE_${Role.OPERATOR.name}")
            .principal(Principal { Application.deskUsername })
            .and()
            .csrf().csrfTokenRepository(csrfTokenRepository())
            .and()
            .authorizeRequests()
            .antMatchers("/**").permitAll()
    }

    @Bean
    fun csrfTokenRepository(): CsrfTokenRepository {
        val repo = CookieCsrfTokenRepository.withHttpOnlyFalse()
        repo.setParameterName("_csrf")
        return repo
    }

    override fun authenticationManager(): AuthenticationManager {
        return AuthenticationManager {
            logger.warn("authenticating local user")
            AnonymousAuthenticationToken("local", "admin", mutableListOf(SimpleGrantedAuthority(Role.ADMIN.name)))
        }
    }
}

@Configuration
@Profile("server", "full")
@Order(2)
class BasicAuthWebSecurity : WebSecurityConfig() {
    override fun configure(http: HttpSecurity) {
        http.requestMatcher { it.requestURI.startsWith("/admin/api/") }
            .csrf().disable()
            .authorizeRequests()
            .mvcMatchers("/").hasAnyRole(Role.OPERATOR.name)
            .and()
            .httpBasic()
    }
}

@Configuration
@Profile("server", "full")
@Order(3)
class FormLoginWebSecurity: WebSecurityConfig() {
    override fun configure(http: HttpSecurity) {
        http.csrf().csrfTokenRepository(csrfTokenRepository())
            .and()
            .authorizeRequests()
            .antMatchers("/file/**", "/images/**", "/api/events/**", "/favicon.ico").permitAll()
            .antMatchers("/**").hasAnyRole(Role.ADMIN.name)
            .and()
            .formLogin()
    }

    @Bean
    fun csrfTokenRepository(): CsrfTokenRepository {
        val repo = CookieCsrfTokenRepository.withHttpOnlyFalse()
        repo.setParameterName("_csrf")
        return repo
    }
}
@Configuration
@Profile("printer")
class PrinterWebSecurity: WebSecurityConfigurerAdapter() {
    override fun configure(auth: AuthenticationManagerBuilder) {
        auth.inMemoryAuthentication()
            .withUser("printer").password("printer").roles("PRINTER")
    }
    override fun configure(http: HttpSecurity) {
        http.csrf().disable()
            .authorizeRequests()
            .antMatchers("/api/printers/**").authenticated()
            .antMatchers("/**").denyAll()
            .and()
            .httpBasic()
    }
}

@Configuration
@Profile("!dev")
class MvcConfiguration(@Value("\${alfio.version}") val alfioVersion: String, private val environment: Environment): WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        if(environment.acceptsProfiles(Profiles.of("server", "full"))) {
            val baseDir = "classpath:/META-INF/resources/webjars/alfio-pi-frontend/$alfioVersion"
            registry.addResourceHandler("/index.html", "/*.js", "/*.map", "/*.js.gz", "/*.css", "/favicon.ico", "/*.woff", "/*.ttf", "/*.woff2", "/*.eot", "/*.svg")
                .addResourceLocations("$baseDir/").setCachePeriod(15 * 60)
            registry.addResourceHandler("/assets/*.png", "/assets/*.css", "/assets/*.map")
                .addResourceLocations("$baseDir/assets/").setCachePeriod(15 * 60)
        }
    }
}

@Configuration
@EnableWebSocket
@Profile("server", "full")
class WebSocketConfiguration(private val systemEventHandler: SystemEventHandlerImpl): WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(systemEventHandler, "/api/internal/ws/stream")
    }
}

data class ConnectionDescriptor(val url: String, val username: String, val password: String)
data class RemoteApiAuthenticationDescriptor(val url: String, val username: String?, val password: String?, val apiKey: String?) {

    fun authenticationHeaderValue(): String {
        return if (apiKey != null) {
            "ApiKey $apiKey"
        } else if (username != null && password != null) {
            Credentials.basic(username, password)
        } else {
            throw IllegalStateException()
        }
    }
}

data class RemoteEventFilter(private val events: String?) {
    private val eventNames
        get() = events.orEmpty()
            .split(",")
            .asSequence()
            .filter { it.isNotEmpty() }
            .map { it.trim() }
            .toList()

    fun accept(eventName: String) = eventNames.isEmpty() or eventNames.contains(eventName.trim())

}

data class CategoryColorConfiguration(private val defaultColor: String,
                                      private val customColors: Map<String, String>) {

    fun getColorFor(ticket: Ticket?): String{
        return ticket?.boxColorClass ?:
                if (ticket?.categoryName != null) {
                    customColors.getOrDefault(getCategoryKey(ticket.categoryName), defaultColor)
                } else {
                    defaultColor
                }
    }
}

private val categoryNameCleaner = Regex("[^a-z0-9\\s]")
private val delimiter = Regex("\\s+")

fun getCategoryKey(categoryName: String): String = categoryName.trim()
    .lowercase(Locale.getDefault())
    .replace(categoryNameCleaner, "")
    .split(delimiter)
    .joinToString(separator = "-")

fun main(args: Array<String>) {
    val properties = System.getProperties().entries.joinToString(separator = "\n", transform = {entry -> "${entry.key}=${entry.value}"})
    logger.info("system properties: \n{}", properties)
    val address = retrieveIPAddress()
    System.setProperty("alfio.server.address", address)
    generateSslKeyPair(address)
    if("true" == System.getProperty("openDBConsole")) {
        openDBConsole()
    }
    SpringApplication.run(Application::class.java, *args)
}

private fun retrieveIPAddress(): String {
    var ipAddress = System.getenv("ALFIO_IP")
    while(ipAddress == null) {
        val result = guessIPAddress()
        if(result != null) {
            println("[INIT] - Got IP address: $result")
            ipAddress = result
            break
        } else{
            println("can't get IP Address, retrying in 1 sec.")
            Thread.sleep(1000L)
        }
    }
    return ipAddress
}

private fun guessIPAddress(): String? {
    return NetworkInterface.getNetworkInterfaces().toList()
    .filter { it.isUp }
    .flatMap { it.interfaceAddresses }
    .map {it.address}
    .firstOrNull { !it.isLoopbackAddress }?.hostAddress
}

private fun generateSslKeyPair(hostAddress: String) {
    val keyStorePath = Paths.get(KEYSTORE_FILE.value)
    if(Files.notExists(keyStorePath)) {
        println("[SSL] - generating KeyPair")
        val random = SecureRandom()
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(1024)
        val keyPair = keyPairGenerator.generateKeyPair()
        val issuerName = X500Name("CN=$hostAddress, OU=None, O=None L=None, C=None")
        val serial = BigInteger.valueOf(random.nextLong())
        val inception = ZonedDateTime.now().minusDays(1)
        val signatureAlgorithm = DefaultSignatureAlgorithmIdentifierFinder().find("SHA256withRSA")
        val signer = BcRSAContentSignerBuilder(signatureAlgorithm, DefaultDigestAlgorithmIdentifierFinder().find(signatureAlgorithm)).build(PrivateKeyFactory.createKey(PrivateKeyInfo.getInstance(keyPair.private.encoded)))
        val certificate = JcaX509CertificateConverter().getCertificate(X509v1CertificateBuilder(issuerName, serial, Date.from(inception.toInstant()), Date.from(inception.plusYears(1).toInstant()), issuerName, SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)).build(signer))
        val keyStore = KeyStore.getInstance("JKS")
        keyStore.load(null, KEYSTORE_PASS.value.toCharArray())
        keyStore.setKeyEntry(KEY_ALIAS.value, keyPair.private, KEY_PASS.value.toCharArray(), arrayOf(certificate))
        keyStore.store(Files.newOutputStream(keyStorePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE), KEYSTORE_PASS.value.toCharArray())
        println("[SSL] - done")
    } else {
        println("[SSL] - Skipped KeyPair generation as there is already a file named $keyStorePath")
        println("[SSL] - if you need a new KeyPair, please delete that file and restart Alf.io-PI.")
    }
}


private fun openDBConsole() {
    val cls = ClassUtils.forName("org.hsqldb.util.DatabaseManagerSwing", ClassLoader.getSystemClassLoader())
    val methodInvoker = MethodInvoker()
    methodInvoker.targetClass = cls
    methodInvoker.setStaticMethod("org.hsqldb.util.DatabaseManagerSwing.main")
    methodInvoker.setArguments(arrayOf(arrayOf("--url", "jdbc:hsqldb:file:alfio", "--noexit")))
    methodInvoker.prepare()
    methodInvoker.invoke()
}

enum class Constants(val value: String) {
    KEYSTORE_FILE("alfio-pi-keystore.jks"),
    KEYSTORE_PASS("alfio-pi-keystore"),
    KEY_ALIAS("alfio-pi"),
    KEY_PASS("alfio-pi-key")
}

fun isLocalAddress(address: String) = tryOrDefault<Boolean>().invoke({
    val inetAddress = InetAddress.getByName(address)
    inetAddress.isAnyLocalAddress || inetAddress.isLoopbackAddress || NetworkInterface.getByInetAddress(inetAddress) != null
}, {false})