/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.routing

import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.util.*

/**
 * Represents a result of a route evaluation against a call
 *
 * @param succeeded indicates if a route matches current [RoutingResolveContext]
 * @param quality indicates quality of this route as compared to other sibling routes
 * @param parameters is an instance of [Parameters] with parameters filled by [RouteSelector]
 * @param segmentIncrement is a value indicating how many path segments has been consumed by a selector
 */
@Suppress("RemoveRedundantQualifierName", "PublicApiImplicitType")
public data class RouteSelectorEvaluation(
    val succeeded: Boolean,
    val quality: Double,
    val parameters: Parameters = Parameters.Empty,
    val segmentIncrement: Int = 0
) {
    public companion object {
        /**
         * Quality of [RouteSelectorEvaluation] when a constant value has matched
         */
        public const val qualityConstant: Double = 1.0

        /**
         * Quality of [RouteSelectorEvaluation] when a query parameter has matched
        */
        public const val qualityQueryParameter: Double = 1.0

        /**
         * Quality of [RouteSelectorEvaluation] when a parameter with prefix or suffix has matched
         */
        public const val qualityParameterWithPrefixOrSuffix: Double = 0.9

        /**
         * Generic quality of [RouteSelectorEvaluation] to use as reference when some specific parameter has matched
         */
        public const val qualityParameter: Double = 0.8

        /**
         * Quality of [RouteSelectorEvaluation] when a path parameter has matched
         */
        public const val qualityPathParameter: Double = qualityParameter

        /**
         * Quality of [RouteSelectorEvaluation] when a HTTP method parameter has matched
         */
        public const val qualityMethodParameter: Double = qualityParameter

        /**
         * Quality of [RouteSelectorEvaluation] when a wildcard has matched
         */
        public const val qualityWildcard: Double = 0.5

        /**
         * Quality of [RouteSelectorEvaluation] when an optional parameter was missing
         */
        public const val qualityMissing: Double = 0.2

        /**
         * Quality of [RouteSelectorEvaluation] when a tailcard match has occurred
         */
        public const val qualityTailcard: Double = 0.1

        /**
         * Route evaluation failed to succeed, route doesn't match a context
         */
        public val Failed: RouteSelectorEvaluation = RouteSelectorEvaluation(false, 0.0)

        /**
         * Route evaluation succeeded for a missing optional value
         */
        public val Missing: RouteSelectorEvaluation = RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityMissing)

        /**
         * Route evaluation succeeded for a constant value
         */
        public val Constant: RouteSelectorEvaluation = RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityConstant)

        /**
         * Route evaluation succeeded for a single path segment with a constant value
         */
        public val ConstantPath: RouteSelectorEvaluation =
            RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityConstant, segmentIncrement = 1)

        /**
         * Route evaluation succeeded for a wildcard path segment
         */
        public val WildcardPath: RouteSelectorEvaluation =
            RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityWildcard, segmentIncrement = 1)
    }
}

/**
 * Base type for all routing selectors
 *
 * @param quality indicates how good this selector is compared to siblings
 */
public abstract class RouteSelector(public val quality: Double) {
    /**
     * Evaluates this selector against [context] and a path segment at [segmentIndex]
     */
    public abstract fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation
}

/**
 * The selector for routing root.
 */
@InternalAPI
public class RootRouteSelector(rootPath: String = "") : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    private val parts = RoutingPath.parse(rootPath).parts.map {
        require(it.kind == RoutingPathSegmentKind.Constant) {
            "rootPath should be constant, no wildcards supported."
        }
        it.value
    }
    private val successEvaluationResult = RouteSelectorEvaluation(
        true, RouteSelectorEvaluation.qualityConstant,
        segmentIncrement = parts.size
    )
    private val hasTrailingSlash = rootPath.endsWith('/')

    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        check(segmentIndex == 0) { "Root selector should be evaluated first." }
        if (parts.isEmpty()) {
            return RouteSelectorEvaluation.Constant
        }

        val parts = parts
        val segments = context.segments
        if (segments.size < parts.size || hasTrailingSlash != context.hasTrailingSlash) {
            return RouteSelectorEvaluation.Failed
        }

        for (index in segmentIndex until segmentIndex + parts.size) {
            if (segments[index] != parts[index]) {
                return RouteSelectorEvaluation.Failed
            }
        }

        return successEvaluationResult
    }

    override fun toString(): String = parts.joinToString("/")
}

/**
 * Evaluates a route against a constant query parameter value
 * @param name is a name of the query parameter
 * @param value is a value of the query parameter
 */
public data class ConstantParameterRouteSelector(val name: String, val value: String) :
    RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        if (context.call.parameters.contains(name, value))
            return RouteSelectorEvaluation.Constant
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "[$name = $value]"
}

/**
 * Evaluates a route against a query parameter value and captures its value
 * @param name is a name of the query parameter
 */
public data class ParameterRouteSelector(val name: String) :
    RouteSelector(RouteSelectorEvaluation.qualityQueryParameter) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val param = context.call.parameters.getAll(name)
        if (param != null)
            return RouteSelectorEvaluation(
                true,
                RouteSelectorEvaluation.qualityQueryParameter,
                parametersOf(name, param)
            )
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "[$name]"
}

/**
 * Evaluates a route against an optional query parameter value and captures its value, if found
 * @param name is a name of the query parameter
 */
public data class OptionalParameterRouteSelector(val name: String) :
    RouteSelector(RouteSelectorEvaluation.qualityQueryParameter) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val param = context.call.parameters.getAll(name)
        if (param != null)
            return RouteSelectorEvaluation(
                true,
                RouteSelectorEvaluation.qualityQueryParameter,
                parametersOf(name, param)
            )
        return RouteSelectorEvaluation.Missing
    }

    override fun toString(): String = "[$name?]"
}

/**
 * Evaluates a route against a constant path segment
 * @param value is a value of the path segment
 */
public data class PathSegmentConstantRouteSelector(val value: String) : RouteSelector(RouteSelectorEvaluation.qualityConstant) {

    private var hasTrailingSlash: Boolean = false

    public constructor(value: String, hasTrailingSlash: Boolean): this(value) {
        this.hasTrailingSlash = hasTrailingSlash
    }

    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation = when {
        segmentIndex == context.segments.lastIndex && hasTrailingSlash != context.hasTrailingSlash ->
            RouteSelectorEvaluation.Failed
        segmentIndex < context.segments.size && context.segments[segmentIndex] == value ->
            RouteSelectorEvaluation.ConstantPath
        else -> RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = value

    // autogenerated
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PathSegmentConstantRouteSelector

        if (value != other.value) return false
        if (hasTrailingSlash != other.hasTrailingSlash) return false

        return true
    }

    // autogenerated
    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + hasTrailingSlash.hashCode()
        return result
    }
}

/**
 * Evaluates a route against a parameter path segment and captures its value
 * @param name is the name of the parameter to capture values to
 * @param prefix is an optional suffix
 * @param suffix is an optional prefix
 */
public data class PathSegmentParameterRouteSelector(
    val name: String,
    val prefix: String? = null,
    val suffix: String? = null
) : RouteSelector(RouteSelectorEvaluation.qualityPathParameter) {

    private var hasTrailingSlash: Boolean = false

    public constructor(name: String, prefix: String? = null, suffix: String? = null, hasTrailingSlash: Boolean): this(name, prefix, suffix) {
        this.hasTrailingSlash = hasTrailingSlash
    }

    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        return evaluatePathSegmentParameter(
            segments = context.segments,
            segmentIndex = segmentIndex,
            name = name,
            prefix = prefix,
            suffix = suffix,
            isOptional = false,
            selectorHasTrailingSlash = hasTrailingSlash,
            contextHasTrailingSlash = context.hasTrailingSlash,
        )
    }

    override fun toString(): String = "${prefix ?: ""}{$name}${suffix ?: ""}"

    // autogenerated
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PathSegmentParameterRouteSelector

        if (name != other.name) return false
        if (prefix != other.prefix) return false
        if (suffix != other.suffix) return false
        if (hasTrailingSlash != other.hasTrailingSlash) return false

        return true
    }

    // autogenerated
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (prefix?.hashCode() ?: 0)
        result = 31 * result + (suffix?.hashCode() ?: 0)
        result = 31 * result + hasTrailingSlash.hashCode()
        return result
    }
}

/**
 * Evaluates a route against an optional parameter path segment and captures its value, if any
 * @param name is the name of the parameter to capture values to
 * @param prefix is an optional suffix
 * @param suffix is an optional prefix
 */
public data class PathSegmentOptionalParameterRouteSelector(
    val name: String,
    val prefix: String? = null,
    val suffix: String? = null
) : RouteSelector(RouteSelectorEvaluation.qualityPathParameter) {

    private var hasTrailingSlash: Boolean = false

    public constructor(name: String, prefix: String? = null, suffix: String? = null, hasTrailingSlash: Boolean): this(name, prefix, suffix) {
        this.hasTrailingSlash = hasTrailingSlash
    }

    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        return evaluatePathSegmentParameter(
            segments = context.segments,
            segmentIndex = segmentIndex,
            name = name,
            prefix = prefix,
            suffix = suffix,
            isOptional = true,
            selectorHasTrailingSlash = hasTrailingSlash,
            contextHasTrailingSlash = context.hasTrailingSlash,
        )
    }

    override fun toString(): String = "${prefix ?: ""}{$name?}${suffix ?: ""}"

    // autogenerated
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PathSegmentOptionalParameterRouteSelector

        if (name != other.name) return false
        if (prefix != other.prefix) return false
        if (suffix != other.suffix) return false
        if (hasTrailingSlash != other.hasTrailingSlash) return false

        return true
    }

    // autogenerated
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (prefix?.hashCode() ?: 0)
        result = 31 * result + (suffix?.hashCode() ?: 0)
        result = 31 * result + hasTrailingSlash.hashCode()
        return result
    }
}

/**
 * Evaluates a route against any single path segment
 */
public object PathSegmentWildcardRouteSelector : RouteSelector(RouteSelectorEvaluation.qualityWildcard) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        if (segmentIndex < context.segments.size)
            return RouteSelectorEvaluation.WildcardPath
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "*"
}

/**
 * Evaluates a route against any number of trailing path segments, and captures their values
 * @param name is the name of the parameter to capture values to
 * @property prefix before the tailcard (static text)
 */
public data class PathSegmentTailcardRouteSelector(val name: String = "", val prefix: String = "") : RouteSelector(RouteSelectorEvaluation.qualityTailcard) {

    private var hasTrailingSlash: Boolean = false

    public constructor(name: String = "", prefix: String = "", hasTrailingSlash: Boolean) : this(name, prefix) {
        this.hasTrailingSlash = hasTrailingSlash
    }

    init {
        require(prefix.none { it == '/' }) { "Multisegment prefix is not supported" }
    }

    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        if (prefix.isNotEmpty()) {
            val segmentText = context.segments.getOrNull(segmentIndex)
            if (segmentText == null || !segmentText.startsWith(prefix)) {
                return RouteSelectorEvaluation.Failed
            }
        }

        val values = when {
            name.isEmpty() -> parametersOf()
            else -> parametersOf(name, context.segments.drop(segmentIndex).mapIndexed { index, segment ->
                if (index == 0) segment.drop(prefix.length)
                else segment
            })
        }
        val quality = when {
            segmentIndex < context.segments.size -> RouteSelectorEvaluation.qualityTailcard
            else -> RouteSelectorEvaluation.qualityMissing
        }
        if (hasTrailingSlash != context.hasTrailingSlash) {
            return RouteSelectorEvaluation.Failed
        }
        return RouteSelectorEvaluation(
            true, quality, values,
            segmentIncrement = context.segments.size - segmentIndex
        )
    }

    override fun toString(): String = "{...}"

    // autogenerated
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PathSegmentTailcardRouteSelector

        if (name != other.name) return false
        if (prefix != other.prefix) return false
        if (hasTrailingSlash != other.hasTrailingSlash) return false

        return true
    }

    // autogenerated
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + prefix.hashCode()
        result = 31 * result + hasTrailingSlash.hashCode()
        return result
    }
}

/**
 * Evaluates a route as a result of the OR operation using two other selectors
 * @param first is a first selector
 * @param second is a second selector
 */
public data class OrRouteSelector(val first: RouteSelector, val second: RouteSelector) :
    RouteSelector(first.quality * second.quality) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val result = first.evaluate(context, segmentIndex)
        if (result.succeeded)
            return result
        else
            return second.evaluate(context, segmentIndex)
    }

    override fun toString(): String = "{$first | $second}"
}

/**
 * Evaluates a route as a result of the AND operation using two other selectors
 * @param first is a first selector
 * @param second is a second selector
 */
public data class AndRouteSelector(val first: RouteSelector, val second: RouteSelector) :
    RouteSelector(first.quality * second.quality) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val result1 = first.evaluate(context, segmentIndex)
        if (!result1.succeeded)
            return result1
        val result2 = second.evaluate(context, segmentIndex + result1.segmentIncrement)
        if (!result2.succeeded)
            return result2
        val resultValues = result1.parameters + result2.parameters
        return RouteSelectorEvaluation(
            true,
            result1.quality * result2.quality,
            resultValues,
            result1.segmentIncrement + result2.segmentIncrement
        )
    }

    override fun toString(): String = "{$first & $second}"
}

/**
 * Evaluates a route against an [HttpMethod]
 * @param method is an instance of [HttpMethod]
 */
public data class HttpMethodRouteSelector(val method: HttpMethod) :
    RouteSelector(RouteSelectorEvaluation.qualityMethodParameter) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        if (context.call.request.httpMethod == method)
            return RouteSelectorEvaluation.Constant
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "(method:${method.value})"
}

/**
 * Evaluates a route against a header in the request
 * @param name is a name of the header
 * @param value is a value of the header
 */
public data class HttpHeaderRouteSelector(val name: String, val value: String) :
    RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val headers = context.call.request.headers[name]
        val parsedHeaders = parseAndSortHeader(headers)
        val header = parsedHeaders.firstOrNull { it.value.equals(value, ignoreCase = true) }
        if (header != null)
            return RouteSelectorEvaluation(true, header.quality)
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "(header:$name = $value)"
}

/**
 * Evaluates a route against a content-type in the [HttpHeaders.Accept] header in the request
 * @param contentType is an instance of [ContentType]
 */
public data class HttpAcceptRouteSelector(val contentType: ContentType) :
    RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val acceptHeaderContent = context.call.request.headers[HttpHeaders.Accept]
        try {
            val parsedHeaders = parseAndSortContentTypeHeader(acceptHeaderContent)

            if (parsedHeaders.isEmpty())
                return RouteSelectorEvaluation.Missing
            val header = parsedHeaders.firstOrNull { contentType.match(it.value) }
            if (header != null)
                return RouteSelectorEvaluation(true, header.quality)
            return RouteSelectorEvaluation.Failed
        } catch (failedToParse: BadContentTypeFormatException) {
            throw BadRequestException("Illegal Accept header format: $acceptHeaderContent", failedToParse)
        }
    }

    override fun toString(): String = "(contentType:$contentType)"
}

internal fun evaluatePathSegmentParameter(
    segments: List<String>,
    segmentIndex: Int,
    name: String,
    prefix: String?,
    suffix: String?,
    isOptional: Boolean,
    selectorHasTrailingSlash: Boolean,
    contextHasTrailingSlash: Boolean
): RouteSelectorEvaluation {
    fun failedEvaluation(): RouteSelectorEvaluation {
        return if (isOptional) RouteSelectorEvaluation.Missing else RouteSelectorEvaluation.Failed
    }

    if (segmentIndex == segments.lastIndex && selectorHasTrailingSlash != contextHasTrailingSlash) {
        return RouteSelectorEvaluation.Failed
    }

    if (segmentIndex >= segments.size) {
        return failedEvaluation()
    }

    val part = segments[segmentIndex]
    val prefixChecked = when {
        prefix == null -> part
        part.startsWith(prefix) -> part.drop(prefix.length)
        else -> return failedEvaluation()
    }

    val suffixChecked = when {
        suffix == null -> prefixChecked
        prefixChecked.endsWith(suffix) -> prefixChecked.dropLast(suffix.length)
        else -> return failedEvaluation()
    }

    val values = parametersOf(name, suffixChecked)
    return RouteSelectorEvaluation(
        succeeded = true,
        quality = if (prefix.isNullOrEmpty() && suffix.isNullOrEmpty()) RouteSelectorEvaluation.qualityPathParameter
        else RouteSelectorEvaluation.qualityParameterWithPrefixOrSuffix,
        parameters = values,
        segmentIncrement = 1
    )
}
