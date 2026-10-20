package org.im0x00.com.benchmark1

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class NotiMvcApplication

fun main(args: Array<String>) {
    runApplication<NotiMvcApplication>(*args)
}