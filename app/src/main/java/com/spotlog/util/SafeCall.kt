package com.spotlog.util

import android.util.Log
import kotlinx.coroutines.CancellationException

suspend inline fun <T> safeCall(
    crossinline onError: suspend (String) -> Unit,
    errorMessage: String,
    crossinline block: suspend () -> T
): T? {
    return try {
        block()
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        Log.e("SafeCall", "$errorMessage: ${e.message}", e)
        onError(errorMessage)
        null
    }
}