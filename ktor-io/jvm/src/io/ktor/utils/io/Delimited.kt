package io.ktor.utils.io

import io.ktor.utils.io.internal.*
import java.io.*
import java.nio.*

/**
 * Reads from the channel to the specified [dst] byte buffer until one of the following:
 * - channel's end
 * - [dst] capacity exceeded
 * - [delimiter] bytes encountered
 *
 * If [delimiter] bytes encountered then these bytes remain unconsumed
 *
 * @return non-negative number of copied bytes, possibly 0
 */
public suspend fun ByteReadChannel.readUntilDelimiter(delimiter: ByteBuffer, dst: ByteBuffer): Int {
    require(delimiter.hasRemaining())
    require(delimiter !== dst)

    var copied = 0
    var endFound = false

    while (!isClosedForRead && !endFound && dst.hasRemaining()) {
        read {
        }
    }

    lookAhead {
        do {
            val rc = tryCopyUntilDelimiter(delimiter, dst)
            if (rc == 0) break
            val size = if (rc < 0) {
                endFound = true
                -rc
            } else rc
            copied += size
        } while (dst.hasRemaining() && !endFound)
    }

    return if (copied == 0 && isClosedForRead) -1
    else if (!dst.hasRemaining() || endFound) copied
    else readUntilDelimiterSuspend(delimiter, dst, copied)
}

/**
 * Skip all bytes until [delimiter] in the current channel. The [delimiter] will also be skipped.
 *
 * @throws IOException if the delimiter isn't't found.
 */
public suspend fun ByteReadChannel.skipDelimiter(delimiter: ByteBuffer) {
    require(delimiter.hasRemaining())

    var found = false

    var offset = delimiter.position()
    while (!found && !isClosedForRead) {
        read {
            while (!it.isEmpty() && offset < delimiter.limit()) {
                val matches = it.get() == delimiter[offset]

                if (matches) {
                    offset += 1
                } else {
                    offset = delimiter.position()
                }
            }

            found = offset == delimiter.limit()
        }
    }

    if (!found) {
        throw IOException("Broken delimiter occured")
    }
}

private suspend fun ByteReadChannel.readUntilDelimiterSuspend(delimiter: ByteBuffer, dst: ByteBuffer, copied0: Int): Int {
    require(delimiter !== dst)
    require(copied0 >= 0)

    var endFound = false
    val copied = lookAheadSuspend {
        var copied = copied0

        do {
            awaitAtLeast(1)
            val rc = tryCopyUntilDelimiter(delimiter, dst)
            if (rc == 0) {
                if (startsWithDelimiter(delimiter) == delimiter.remaining()) {
                    endFound = true
                    break
                }
                if (isClosedForWrite) {
                    break
                } else {
                    awaitAtLeast(delimiter.remaining())
                    continue
                }
            }

            val size = if (rc <= 0) {
                endFound = true
                -rc
            } else rc
            copied += size
        } while (dst.hasRemaining() && !endFound)

        copied
    }

    return when {
        copied > 0 && isClosedForWrite && !endFound -> copied + readAvailable(dst).coerceAtLeast(0)
        copied == 0 && isClosedForRead -> -1
        else -> copied
    }
}

/**
 * @return a positive number of bytes copied if no [delimiter] found yet or a negated number of bytes copied if
 * the delimited has been found, or 0 if no buffer available (not yet ready or EOF)
 */
private fun LookAheadSession.tryCopyUntilDelimiter(delimiter: ByteBuffer, dst: ByteBuffer): Int {
    var endFound = false
    val buffer = request(0, 1) ?: return 0
    val index = buffer.indexOfPartial(delimiter)
    val size = if (index != -1) {
        val found = minOf(buffer.remaining() - index, delimiter.remaining())
        val notKnown = delimiter.remaining() - found

        if (notKnown == 0) {
            endFound = true
            dst.putLimited(buffer, buffer.position() + index)
        } else {
            val remembered = buffer.duplicate()
            val next = request(index + found, 1)
            if (next == null) {
                dst.putLimited(remembered, remembered.position() + index)
            } else if (next.startsWith(delimiter, found)) {
                if (next.remaining() >= notKnown) {
                    endFound = true
                    dst.putLimited(remembered, remembered.position() + index)
                } else {
                    dst.putLimited(remembered, remembered.position() + index)
                }
            } else {
                dst.putLimited(remembered, remembered.position() + index + 1)
            }
        }
    } else {
        dst.putAtMost(buffer)
    }
    consumed(size)

    return if (endFound) -size else size
}

/**
 * @return Number of bytes of the delimiter found (possibly 0 if no bytes available yet) or -1 if it doesn't start
 */
private fun LookAheadSession.startsWithDelimiter(delimiter: ByteBuffer): Int {
    val buffer = request(0, 1) ?: return 0
    val index = buffer.indexOfPartial(delimiter)
    if (index != 0) return -1

    val found = minOf(buffer.remaining() - index, delimiter.remaining())
    val notKnown = delimiter.remaining() - found

    if (notKnown > 0) {
        val next = request(index + found, notKnown) ?: return found
        if (!next.startsWith(delimiter, found)) return -1
    }

    return delimiter.remaining()
}
