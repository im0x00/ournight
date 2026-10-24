package org.im0x00.notification.mvcvt

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class NotiMvcApplication

fun main(args: Array<String>) {
    runApplication<NotiMvcApplication>(*args)
}