package org.im0x00.notification.coroutine


import java.util.ArrayDeque

class ReplayBuffer(
    private val capacity: Int,
    start: StreamId,
) {
    private val queue = ArrayDeque<SseEvent>(capacity)
    private var floor = start

    init {
        require(capacity >= 1) { "capacity must be at least 1" }
    }

    fun add(event: SseEvent) {
        require(event.id > floor) { "id must be greater than floor" }
        require(queue.isEmpty() || queue.peekLast().id < event.id) { "id must be greater than the last one" }

        if (queue.size == capacity) {
            floor = queue.removeFirst().id
        }
        queue.addLast(event)
    }

    fun floor(): StreamId = floor

    fun since(from: StreamId): List<SseEvent>? {
        if (from < floor) return null
        return queue.filter { it.id > from }
    }

}
