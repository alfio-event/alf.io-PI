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

import alfio.pi.model.Role
import alfio.pi.repository.AuthorityRepository
import alfio.pi.repository.PrinterRepository
import alfio.pi.repository.UserRepository
import alfio.pi.repository.getSystemPrinters
import alfio.pi.util.PasswordGenerator
import ch.digitalfondue.npjt.QueryFactory
import ch.digitalfondue.npjt.QueryRepositoryScanner
import ch.digitalfondue.npjt.mapper.ZonedDateTimeMapper
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
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
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.util.ClassUtils
import org.springframework.util.MethodInvoker
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter
import java.net.NetworkInterface
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.*
import javax.sql.DataSource


@SpringBootApplication
@EnableTransactionManagement
@EnableScheduling
open class Application {

    private val logger = LoggerFactory.getLogger(Application::class.java)!!

    @Bean
    open fun dataSource(@Qualifier("databaseConfiguration") config: ConnectionDescriptor) : DataSource {
        val dataSource = HikariDataSource()
        dataSource.jdbcUrl = config.url
        dataSource.username = config.username
        dataSource.password = config.password
        return dataSource
    }

    @Bean
    open fun migrator(env: Environment, dataSource: DataSource): Flyway {
        val migration = Flyway()
        migration.dataSource = dataSource

        migration.isValidateOnMigrate = true
        migration.target = MigrationVersion.LATEST
        migration.isOutOfOrder = false

        migration.setLocations("alfio/pi/db/")
        migration.migrate()
        return migration
    }

    @Bean
    open fun getPasswordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    open fun namedParameterJdbcTemplate(dataSource: DataSource): NamedParameterJdbcTemplate = NamedParameterJdbcTemplate(dataSource)

    @Bean
    open fun queryFactory(env: Environment, namedParameterJdbcTemplate: NamedParameterJdbcTemplate): QueryFactory {
        val qf = QueryFactory("HSQLDB", namedParameterJdbcTemplate)
        qf.addColumnMapperFactory(ZonedDateTimeMapper.Factory())
        qf.addParameterConverters(ZonedDateTimeMapper.Converter())
        return qf
    }

    @Bean
    open fun queryRepositoryScanner(queryFactory: QueryFactory): QueryRepositoryScanner = QueryRepositoryScanner(queryFactory, "alfio.pi.repository")

    @Bean
    open fun databaseConfiguration(@Value("\${jdbc.url}") url: String,
                                   @Value("\${jdbc.username}") username: String,
                                   @Value("\${jdbc.password}") password: String): ConnectionDescriptor = ConnectionDescriptor(url, username, password)

    @Bean
    open fun masterConnectionConfiguration(@Value("\${master.url}") url: String,
                                           @Value("\${master.username}") username: String,
                                           @Value("\${master.password}") password: String): ConnectionDescriptor = ConnectionDescriptor(url, username, password)

    @Bean
    open fun localServerURL(env: Environment): String {
        val address = NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.interfaceAddresses }
            .map {it.address}
            .first { it.isSiteLocalAddress && it.hostAddress.startsWith("192") }.hostAddress
        val scheme = if(env.acceptsProfiles("dev")) {
            "http"
        } else {
            "https"
        }
        val port = env.getProperty("server.port", "8080")
        return "$scheme://$address:$port"
    }

    @Bean
    open fun gson(): Gson {
        val builder = GsonBuilder()
        builder.registerTypeAdapter(ZonedDateTime::class.java, getZonedDateTimeSerializer())
        return builder.create()
    }

    fun getZonedDateTimeSerializer(): JsonSerializer<ZonedDateTime> {
        val timeFormatter = DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .toFormatter(Locale.ROOT)
        return JsonSerializer { src, type, jsonSerializationContext -> JsonPrimitive(src.format(DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral('T')
            .append(timeFormatter)
            .appendLiteral('Z')
            .toFormatter(Locale.ROOT))) }
    }

    @Bean
    open fun initializer() = ApplicationListener<ContextRefreshedEvent> {
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
        val printerRepository = applicationContext.getBean(PrinterRepository::class.java)
        val existingPrinters = printerRepository.loadAll()
        getSystemPrinters().filter { sp -> existingPrinters.none { e -> e.name == sp.name }}.forEach {
            printerRepository.insert(it.name, "", true)
        }
    }

}

@EnableWebSecurity
abstract class WebSecurityConfig() : WebSecurityConfigurerAdapter() {

    @Autowired
    open fun authenticationManager(auth: AuthenticationManagerBuilder, passwordEncoder: PasswordEncoder, dataSource: DataSource) {
        auth.jdbcAuthentication()
            .dataSource(dataSource)
            .usersByUsernameQuery("select username, password, true from user where username = ?")
            .authoritiesByUsernameQuery("select username, 'ROLE_' || role from authority where username = ?")
            .passwordEncoder(passwordEncoder)
    }
}

@Configuration
@Order(1)
open class BasicAuthWebSecurity : WebSecurityConfig() {
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
@Order(2)
open class FormLoginWebSecurity: WebSecurityConfig() {
    override fun configure(http: HttpSecurity) {
        http.csrf().csrfTokenRepository(csrfTokenRepository())
            .and()
            .authorizeRequests()
            .antMatchers("/file/**", "/images/**", "/api/events/**").permitAll()
            .antMatchers("/**").hasAnyRole(Role.ADMIN.name)
            .and()
            .formLogin()
    }

    @Bean
    open fun csrfTokenRepository(): CsrfTokenRepository {
        val repo = CookieCsrfTokenRepository.withHttpOnlyFalse()
        repo.setParameterName("_csrf")
        return repo
    }
}

@Configuration
@Profile("!dev")
open class MvcConfiguration(@Value("\${alfio.version}") val alfioVersion: String): WebMvcConfigurerAdapter() {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val baseDir = "classpath:/META-INF/resources/webjars/alfio-pi-frontend/$alfioVersion/"
        registry.addResourceHandler("/index.html", "/*.js", "/*.map", "/*.js.gz", "/*.css", "/favicon.ico", "/assets/*.css")
            .addResourceLocations(baseDir).setCachePeriod(15 * 60)
    }
}


data class ConnectionDescriptor(val url: String, val username: String, val password: String)

fun main(args: Array<String>) {
    println("current directory: ${System.getProperty("user.dir")}")
    SpringApplication.run(Application::class.java, *args)
}

private fun openDBConsole() {
    val cls = ClassUtils.forName("org.hsqldb.util.DatabaseManagerSwing", ClassLoader.getSystemClassLoader())
    val methodInvoker = MethodInvoker()
    methodInvoker.targetClass = cls
    methodInvoker.setStaticMethod("org.hsqldb.util.DatabaseManagerSwing.main")
    methodInvoker.arguments = arrayOf(arrayOf("--url", "jdbc:hsqldb:mem:alfio", "--noexit"))
    methodInvoker.prepare()
    methodInvoker.invoke()
}