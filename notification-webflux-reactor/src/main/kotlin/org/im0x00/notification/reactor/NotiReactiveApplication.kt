package org.im0x00.notification.reactor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class NotiReactiveApplication

fun main(args: Array<String>) {
    runApplication<NotiReactiveApplication>(*args)
}
