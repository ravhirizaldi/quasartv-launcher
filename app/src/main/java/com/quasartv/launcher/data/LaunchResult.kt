package com.quasartv.launcher.data

sealed class LaunchResult {
    data class Success(val packageName: String) : LaunchResult()

    data class Unavailable(
        val reason: String,
        val packageName: String? = null
    ) : LaunchResult()

    data class Failed(
        val reason: String,
        val packageName: String? = null,
        val cause: Throwable? = null
    ) : LaunchResult()
}
