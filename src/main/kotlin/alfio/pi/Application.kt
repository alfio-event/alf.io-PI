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

import alfio.pi.repository.PrinterRepository
import ch.digitalfondue.npjt.QueryFactory
import ch.digitalfondue.npjt.QueryRepositoryScanner
import ch.digitalfondue.npjt.mapper.ZonedDateTimeMapper
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.zaxxer.hikari.HikariDataSource
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.util.Matrix
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
import javax.sql.DataSource


@SpringBootApplication
@EnableTransactionManagement
@EnableScheduling
open class Application {
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

    @Bean
    open fun databaseConfiguration(@Value("\${jdbc.url}") url: String,
                                   @Value("\${jdbc.username}") username: String,
                                   @Value("\${jdbc.password}") password: String): ConnectionDescriptor = ConnectionDescriptor(url, username, password)

    @Bean
    open fun masterConnectionConfiguration(@Value("\${master.url}") url: String,
                                           @Value("\${master.username}") username: String,
                                           @Value("\${master.password}") password: String): ConnectionDescriptor = ConnectionDescriptor(url, username, password)
}

@Configuration
@EnableWebSecurity
open class WebSecurityConfig : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        http.csrf().disable()
    }

    @Autowired
    @Throws(Exception::class)
    fun configureGlobal(auth: AuthenticationManagerBuilder) {
        auth.inMemoryAuthentication().withUser("admin").password("abcd").roles("USER")
    }
}

data class ConnectionDescriptor(val url: String, val username: String, val password: String)

fun main(args: Array<String>) {

    val printerByLocation = PrinterRepository.findPrinterByName("DYMO_LabelWriter_450_Turbo")
    println("found $printerByLocation")

    //val pdf = generatePDF("First", "Last", "Company name", UUID.randomUUID().toString())
    //val printService = PrinterRepository.getActivePrinters()[2]
    //val printJob = printService.createPrintJob()
    //printJob.print(SimpleDoc(pdf, DocFlavor.BYTE_ARRAY.PDF, null), null)


    SpringApplication.run(Application::class.java, *args)
}
private fun generatePDF(firstName: String, lastName: String, company: String, ticketUUID: String): ByteArray {
    val document = PDDocument()
    val font = PDType0Font.load(document, Application::class.java.getResourceAsStream("/font/DejaVuSansMono.ttf"))
    val page = PDPage(PDRectangle(convertMMToPoint(41F), convertMMToPoint(89F)))
    val pageWidth = page.mediaBox.width
    document.addPage(page)
    val qr = LosslessFactory.createFromImage(document, generateQRCode(ticketUUID))
    val contentStream = PDPageContentStream(document, page, PDPageContentStream.AppendMode.OVERWRITE, false)
    contentStream.transform(Matrix(0F, 1F, -1F, 0F, pageWidth, 0F))
    contentStream.setFont(font, 24F)
    contentStream.beginText()
    contentStream.newLineAtOffset(10F, 70F)
    contentStream.showText(firstName)
    contentStream.setFont(font, 16F)
    contentStream.newLineAtOffset(0F, -20F)
    contentStream.showText(lastName)
    contentStream.setFont(font, 10F)
    contentStream.newLineAtOffset(0F, -20F)
    contentStream.showText(company)
    contentStream.endText()
    contentStream.drawImage(qr, 175F, 30F, 70F, 70F)
    contentStream.setFont(font, 9F)
    contentStream.beginText()
    contentStream.newLineAtOffset(189F, 25F)
    contentStream.showText(ticketUUID.substringBefore('-').toUpperCase())
    contentStream.close()
    val out = ByteArrayOutputStream()
    document.save(out)
    document.close()
    return out.toByteArray()
}

private val convertMMToPoint: (Float) -> Float = {
    it * (1 / (10 * 2.54f) * 72)
}

private fun generateQRCode(value: String): BufferedImage {
    val hintMap = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
    hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 200, 200, hintMap)
    return MatrixToImageWriter.toBufferedImage(matrix)
}