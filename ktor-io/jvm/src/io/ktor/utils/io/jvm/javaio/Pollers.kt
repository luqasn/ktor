/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:JvmName("PollersKt")

package io.ktor.utils.io.jvm.javaio

import java.util.concurrent.locks.*

private val parkingImplLocal = ThreadLocal<Parking<Thread>?>()

@get:JvmName("getParkingImpl")
internal val parkingImpl: Parking<Thread>
    get() = parkingImplLocal.get() ?: DefaultParking

@Suppress("unused")
@JvmName("prohibitParking")
internal fun prohibitParking() {
    parkingImplLocal.set(ProhibitParking)
}

@JvmName("isParkingAllowed")
internal fun isParkingAllowed(): Boolean {
    return parkingImpl !== ProhibitParking
}

internal interface Parking<T : Any> {
    fun token(): T
    fun park(timeNanos: Long)
    fun unpark(token: T)
}

private object DefaultParking : Parking<Thread> {
    override fun token(): Thread {
        return Thread.currentThread()
    }

    override fun park(timeNanos: Long) {
        require(timeNanos >= 0L)
        LockSupport.parkNanos(timeNanos)
    }

    override fun unpark(token: Thread) {
        LockSupport.unpark(token)
    }
}

private object ProhibitParking : Parking<Thread> {
    override fun token(): Thread {
        fail()
    }

    override fun park(timeNanos: Long) {
        fail()
    }

    override fun unpark(token: Thread) {
        fail()
    }

    private fun fail(): Nothing {
        throw UnsupportedOperationException("Parking is prohibited on this thread.")
    }
}
