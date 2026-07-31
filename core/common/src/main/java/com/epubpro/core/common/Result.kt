package com.epubpro.core.common

sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Error(val exception: Throwable, val message: String = exception.localizedMessage ?: "Unknown Error") : Result<Nothing>
    object Loading : Result<Nothing>
}
