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

import ch.digitalfondue.npjt.QueryFactory
import ch.digitalfondue.npjt.QueryRepositoryScanner
import ch.digitalfondue.npjt.mapper.ZonedDateTimeMapper
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.sql.DataSource
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder



@SpringBootApplication
@EnableTransactionManagement
open class Application {
    @Bean
    open fun dataSource() : DataSource {
        val dataSource = HikariDataSource()
        dataSource.jdbcUrl = "jdbc:hsqldb:file:db/alfio"
        dataSource.username = "sa"
        dataSource.password = ""
        dataSource.maximumPoolSize = 10
        return dataSource
    }

    @Bean
    open fun migrator(env: Environment, dataSource: DataSource): Flyway {
        val migration = Flyway()
        migration.dataSource = dataSource

        migration.isValidateOnMigrate = true
        migration.target = MigrationVersion.LATEST
        migration.isOutOfOrder = true

        migration.setLocations("alfio/pi/db/")
        migration.migrate()
        return migration
    }

    @Bean
    open fun namedParameterJdbcTemplate(dataSource: DataSource): NamedParameterJdbcTemplate {
        return NamedParameterJdbcTemplate(dataSource)
    }

    @Bean
    open fun queryFactory(env: Environment, namedParameterJdbcTemplate: NamedParameterJdbcTemplate): QueryFactory {
        val qf = QueryFactory("HSQLDB", namedParameterJdbcTemplate)
        qf.addColumnMapperFactory(ZonedDateTimeMapper.Factory())
        qf.addParameterConverters(ZonedDateTimeMapper.Converter())
        return qf
    }

    @Bean
    open fun queryRepositoryScanner(queryFactory: QueryFactory): QueryRepositoryScanner {
        return QueryRepositoryScanner(queryFactory, "alfio.pi.repository")
    }
}

@Configuration
@EnableWebSecurity
open class WebSecurityConfig : WebSecurityConfigurerAdapter() {

    @Autowired
    @Throws(Exception::class)
    fun configureGlobal(auth: AuthenticationManagerBuilder) {
        auth.inMemoryAuthentication().withUser("admin").password("abcd").roles("USER")
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(Application::class.java, *args)
}