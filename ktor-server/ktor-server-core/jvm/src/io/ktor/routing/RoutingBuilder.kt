/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("unused")

package io.ktor.routing

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.util.pipeline.*
import io.ktor.request.*

/**
 * Builds a route to match specified [path]
 */
@ContextDsl
public fun Route.route(path: String, build: Route.() -> Unit): Route = createRouteFromPath(path).apply(build)

/**
 * Builds a route to match specified [method] and [path]
 */
@ContextDsl
public fun Route.route(path: String, method: HttpMethod, build: Route.() -> Unit): Route {
    val selector = HttpMethodRouteSelector(method)
    return createRouteFromPath(path).createChild(selector).apply(build)
}

/**
 * Builds a route to match specified [method]
 */
@ContextDsl
public fun Route.method(method: HttpMethod, body: Route.() -> Unit): Route {
    val selector = HttpMethodRouteSelector(method)
    return createChild(selector).apply(body)
}

/**
 * Builds a route to match parameter with specified [name] and [value]
 */
@ContextDsl
public fun Route.param(name: String, value: String, build: Route.() -> Unit): Route {
    val selector = ConstantParameterRouteSelector(name, value)
    return createChild(selector).apply(build)
}

/**
 * Builds a route to match parameter with specified [name] and capture its value
 */
@ContextDsl
public fun Route.param(name: String, build: Route.() -> Unit): Route {
    val selector = ParameterRouteSelector(name)
    return createChild(selector).apply(build)
}

/**
 * Builds a route to optionally capture parameter with specified [name], if it exists
 */
@ContextDsl
public fun Route.optionalParam(name: String, build: Route.() -> Unit): Route {
    val selector = OptionalParameterRouteSelector(name)
    return createChild(selector).apply(build)
}

/**
 * Builds a route to match header with specified [name] and [value]
 */
@ContextDsl
public fun Route.header(name: String, value: String, build: Route.() -> Unit): Route {
    val selector = HttpHeaderRouteSelector(name, value)
    return createChild(selector).apply(build)
}

/**
 * Builds a route to match requests with [HttpHeaders.Accept] header matching specified [contentType]
 */
@ContextDsl
public fun Route.accept(contentType: ContentType, build: Route.() -> Unit): Route {
    val selector = HttpAcceptRouteSelector(contentType)
    return createChild(selector).apply(build)
}

/**
 * Builds a route to match requests with [HttpHeaders.ContentType] header matching specified [contentType]
 */
@ContextDsl
public fun Route.contentType(contentType: ContentType, build: Route.() -> Unit): Route {
    return header(HttpHeaders.ContentType, "${contentType.contentType}/${contentType.contentSubtype}", build)
}

/**
 * Builds a route to match `GET` requests with specified [path]
 */
@ContextDsl
public fun Route.get(path: String, body: PipelineInterceptor<Unit, ApplicationCall>): Route {
    return route(path, HttpMethod.Get) { handle(body) }
}

/**
 * Builds a route to match `GET` requests
 */
@ContextDsl
public fun Route.get(body: PipelineInterceptor<Unit, ApplicationCall>): Route {
    return method(HttpMethod.Get) { handle(body) }
}

/**
 * Builds a route to match `POST` requests with specified [path]
 */
@ContextDsl
public fun Route.post(path: String, body: PipelineInterceptor<Unit, ApplicationCall>): Route {
    return route(path, HttpMethod.Post) { handle(body) }
}

/**
 * Builds a route to match `POST` requests with specified [path] receiving request body content of type [R]
 */
@ContextDsl
@JvmName("postTyped")
public inline fun <reified R : Any> Route.post(
    path: String,
    crossinline body: suspend PipelineContext<Unit, ApplicationCall>.(R) -> Unit
): Route {
    return route(path, HttpMethod.Post) {
        handle {
            body(call.receive())
        }
    }
}

/**
 * Builds a route to match `POST` requests
 */
@ContextDsl
public fun Route.post(body: PipelineInterceptor<Unit, ApplicationCall>): Route {
    return method(HttpMethod.Post) { handle(body) }
}

/**
 * Builds a route to match `HEAD` requests with specified [path]
 */
@ContextDsl
public fun Route.head(path: String, body: PipelineInterceptor<Unit, ApplicationCall>): Route {
    return route(path, HttpMethod.Head) { handle(body) }
}

/**
 * Builds a route to match `HEAD` requests
 */
@ContextDsl
public fun Route.head(body: PipelineInterceptor<Unit, ApplicationCall>): Route {
    return method(HttpMethod.Head) { handle(body) }
}

/**
 * Builds a route to match `PUT` requests with specified [path]
 */
@ContextDsl
public fun Route.put(path: String, body: PipelineInterceptor<Unit, ApplicationCall>): Route {
    return route(path, HttpMethod.Put) { handle(body) }
}

/**
 * Builds a route to match `PUT` requests
 */
@ContextDsl
public fun Route.put(body: PipelineInterceptor<Unit, ApplicationCall>): Route {
    return method(HttpMethod.Put) { handle(body) }
}

/**
 * Builds a route to match `PATCH` requests with specified [path]
 */
@ContextDsl
public fun Route.patch(path: String, body: PipelineInterceptor<Unit, ApplicationCall>): Route {
    return route(path, HttpMethod.Patch) { handle(body) }
}

/**
 * Builds a route to match `PATCH` requests
 */
@ContextDsl
public fun Route.patch(body: PipelineInterceptor<Unit, ApplicationCall>): Route {
    return method(HttpMethod.Patch) { handle(body) }
}

/**
 * Builds a route to match `DELETE` requests with specified [path]
 */
@ContextDsl
public fun Route.delete(path: String, body: PipelineInterceptor<Unit, ApplicationCall>): Route {
    return route(path, HttpMethod.Delete) { handle(body) }
}

/**
 * Builds a route to match `DELETE` requests
 */
@ContextDsl
public fun Route.delete(body: PipelineInterceptor<Unit, ApplicationCall>): Route {
    return method(HttpMethod.Delete) { handle(body) }
}

/**
 * Builds a route to match `OPTIONS` requests with specified [path]
 */
@ContextDsl
public fun Route.options(path: String, body: PipelineInterceptor<Unit, ApplicationCall>): Route {
    return route(path, HttpMethod.Options) { handle(body) }
}

/**
 * Builds a route to match `OPTIONS` requests
 */
@ContextDsl
public fun Route.options(body: PipelineInterceptor<Unit, ApplicationCall>): Route {
    return method(HttpMethod.Options) { handle(body) }
}

/**
 * Create a routing entry for specified path
 */
public fun Route.createRouteFromPath(path: String): Route {
    val parts = RoutingPath.parse(path).parts
    var current: Route = this
    for (index in parts.indices) {
        val (value, kind) = parts[index]
        val hasTrailingSlash = index == parts.lastIndex && path.endsWith('/')
        val selector = when (kind) {
            RoutingPathSegmentKind.Parameter -> PathSegmentSelectorBuilder.parseParameter(value, hasTrailingSlash)
            RoutingPathSegmentKind.Constant -> PathSegmentSelectorBuilder.parseConstant(value, hasTrailingSlash)
        }
        // there may already be entry with same selector, so join them
        current = current.createChild(selector)
    }
    return current
}

/**
 * Helper object for building instances of [RouteSelector] from path segments
 */
public object PathSegmentSelectorBuilder {
    /**
     * Builds a [RouteSelector] to match a path segment parameter with prefix/suffix and a name
     */
    public fun parseParameter(value: String): RouteSelector = parseParameter(value, false)

    /**
     * Builds a [RouteSelector] to match a path segment parameter with prefix/suffix, name and trailing slash if any
     */
    public fun parseParameter(value: String, hasTrailingSlash: Boolean): RouteSelector {
        val prefixIndex = value.indexOf('{')
        val suffixIndex = value.lastIndexOf('}')

        val prefix = if (prefixIndex == 0) null else value.substring(0, prefixIndex)
        val suffix = if (suffixIndex == value.length - 1) null else value.substring(suffixIndex + 1)

        val signature = value.substring(prefixIndex + 1, suffixIndex)
        return when {
            signature.endsWith("?") -> PathSegmentOptionalParameterRouteSelector(signature.dropLast(1), prefix, suffix, hasTrailingSlash)
            signature.endsWith("...") -> {
                if (suffix != null && suffix.isNotEmpty()) {
                    throw IllegalArgumentException("Suffix after tailcard is not supported")
                }
                PathSegmentTailcardRouteSelector(signature.dropLast(3), prefix ?: "", hasTrailingSlash)
            }
            else -> PathSegmentParameterRouteSelector(signature, prefix, suffix, hasTrailingSlash)
        }
    }

    /**
     * Builds a [RouteSelector] to match a constant or wildcard segment parameter
     */
    public fun parseConstant(value: String): RouteSelector = parseConstant(value, false)

    /**
     * Builds a [RouteSelector] to match a constant or wildcard segment parameter and trailing slash if any
     */
    public fun parseConstant(value: String, hasTrailingSlash: Boolean): RouteSelector = when (value) {
        "*" -> PathSegmentWildcardRouteSelector
        else -> PathSegmentConstantRouteSelector(value, hasTrailingSlash)
    }

    /**
     * Parses a name out of segment specification
     */
    public fun parseName(value: String): String {
        val prefix = value.substringBefore('{', "")
        val suffix = value.substringAfterLast('}', "")
        val signature = value.substring(prefix.length + 1, value.length - suffix.length - 1)
        return when {
            signature.endsWith("?") -> signature.dropLast(1)
            signature.endsWith("...") -> signature.dropLast(3)
            else -> signature
        }
    }
}
