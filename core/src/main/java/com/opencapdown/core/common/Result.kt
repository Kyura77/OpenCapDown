package com.opencapdown.core.common

sealed class OpenCapDownResult<out T> {
    data class Success<T>(val data: T) : OpenCapDownResult<T>()
    data class Failure(val error: SourceError) : OpenCapDownResult<Nothing>()
}

sealed class SourceError {
    data class Network(val message: String) : SourceError()
    data class Parse(val message: String) : SourceError()
    data class SourceNotFound(val sourceId: String) : SourceError()
    data class Unknown(val throwable: Throwable) : SourceError()
}
