package org.im0x00.notification.coroutine

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext

// log helper: which thread and which coroutine is running this line
suspend fun who(): String =
    "[thread=${Thread.currentThread().name} coroutine=${currentCoroutineContext()[CoroutineName]?.name ?: "-"}]"
