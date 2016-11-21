package alfio

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
open class AlfioPiApplication

fun main(args: Array<String>) {
    SpringApplication.run(AlfioPiApplication::class.java, *args)
}
