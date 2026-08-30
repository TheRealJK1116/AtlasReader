package com.atlasreader.core.common

/**
 * Lightweight result wrapper used across engine, importer and repository boundaries.
 * Never throws across a clean architecture boundary: failures are modelled as values.
 */
sealed interface AtlasResult<out T> {
    data class Success<T>(val value: T) : AtlasResult<T>
    data class Failure(val error: AtlasError) : AtlasResult<Nothing>

    fun <R> map(transform: (T) -> R): AtlasResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun <R> flatMap(transform: (T) -> AtlasResult<R>): AtlasResult<R> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }

    fun getOrNull(): T? = (this as? Success)?.value
    fun errorOrNull(): AtlasError? = (this as? Failure)?.error
}

/** Typed error taxonomy. [AtlasError.Unknown] should never escape the engine layer. */
sealed interface AtlasError {
    data class Engine(val format: String, val detail: String) : AtlasError
    data class Import(val detail: String) : AtlasError
    data class Index(val detail: String) : AtlasError
    data class Io(val detail: String) : AtlasError
    data object NotFound : AtlasError
    data object Unknown : AtlasError
}
