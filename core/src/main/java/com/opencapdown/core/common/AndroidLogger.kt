package com.opencapdown.core.common

import android.util.Log

internal class AndroidLogger : Logger {
    override fun d(tag: String, message: String) { Log.d(tag, message) }
    override fun e(tag: String, message: String, throwable: Throwable?) { Log.e(tag, message, throwable) }
}
