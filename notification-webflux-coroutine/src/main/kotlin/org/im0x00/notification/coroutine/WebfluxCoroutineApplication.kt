package org.im0x00.notification.coroutine

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class WebfluxCoroutineApplication

fun main(args: Array<String>) {
    runApplication<WebfluxCoroutineApplication>(*args)
}
