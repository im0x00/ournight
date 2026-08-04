package com.ournight.pos

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PosNotificationWebfluxApplication

fun main(args: Array<String>) {
    runApplication<PosNotificationWebfluxApplication>(*args)
}
