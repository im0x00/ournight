package org.im0x00.com.noti_reactive

// Redis stream entry id "<ms>-<seq>".
// String comparison is wrong ("…-10" < "…-9" lexicographically), so compare both parts as numbers.
data class StreamId(val ms: Long, val seq: Long) : Comparable<StreamId> {

    override fun compareTo(other: StreamId): Int =
        compareValuesBy(this, other, StreamId::ms, StreamId::seq)

    override fun toString() = "$ms-$seq"

    companion object {
        val ZERO = StreamId(0, 0)

        fun parse(id: String?): StreamId? {
            if (id == null) {
                return null
            }
            val dash = id.indexOf('-')
            return if (dash < 0) StreamId(id.toLong(), 0)
            else StreamId(id.substring(0, dash).toLong(), id.substring(dash + 1).toLong())
        }
    }

}
