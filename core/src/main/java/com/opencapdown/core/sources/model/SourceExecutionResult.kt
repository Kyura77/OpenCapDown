package com.opencapdown.core.sources.model

sealed class SourceExecutionResult<out T> {
    data class Success<T>(val data: T) : SourceExecutionResult<T>()
    data class Error(val error: String) : SourceExecutionResult<Nothing>()
}
