/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import kotlinx.coroutines.*
import java.lang.reflect.*
import kotlin.coroutines.*

private val isParkingAllowedFunction: Method? by lazy {
    try {
        Class.forName("io.ktor.utils.io.jvm.javaio.PollersKt")
            .getMethod("isParkingAllowed")
    } catch (cause: Throwable) {
        null
    }
}

internal fun safeToRunInPlace(): Boolean {
    val isParkingAllowed = isParkingAllowedFunction
    return isParkingAllowed != null && try {
        isParkingAllowed.invoke(null) == true
    } catch (cause: Throwable) {
        false
    }
}

@OptIn(ExperimentalStdlibApi::class)
internal suspend fun safeToRunInPlaceSuspend(): Boolean {
    //coroutineContext[CoroutineDispatcher]

    val isParkingAllowed = isParkingAllowedFunction
    return isParkingAllowed != null && try {
        isParkingAllowed.invoke(null) == true
    } catch (cause: Throwable) {
        false
    }
}
