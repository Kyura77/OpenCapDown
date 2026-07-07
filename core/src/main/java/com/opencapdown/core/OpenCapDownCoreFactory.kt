package com.opencapdown.core

import android.content.Context

object OpenCapDownCoreFactory {
    internal const val VERSION = "1.0.0"

    fun create(context: Context): OpenCapDownCore {
        val coreModule = com.opencapdown.core.di.CoreModule(context.applicationContext)
        return coreModule.createCore(VERSION)
    }
}
