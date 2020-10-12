/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.content

import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.*

/**
 * [OutgoingContent] to respond with [OutputStream].
 * The stream would be automatically closed after [body] finish.
 */
public class OutputStreamContent(
    private val body: suspend OutputStream.() -> Unit,
    override val contentType: ContentType,
    override val status: HttpStatusCode? = null
) : OutgoingContent.WriteChannelContent() {

    override suspend fun writeTo(channel: ByteWriteChannel) {
        val stream = channel.toOutputStream()

        withBlocking {
            // use bock should be inside because closing OutputStream is blocking as well
            // and should not be invoked in a epoll/kqueue/reactor thread
            stream.use {
                it.body()
            }
        }
    }
}
