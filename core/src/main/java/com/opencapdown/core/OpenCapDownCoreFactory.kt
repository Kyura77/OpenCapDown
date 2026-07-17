package com.opencapdown.core

import android.content.Context

object OpenCapDownCoreFactory {
    internal         const val VERSION = "1.0.4"

    fun create(context: Context): OpenCapDownCore {
        val coreModule = com.opencapdown.core.di.CoreModule(context.applicationContext)
        val core = coreModule.createCore(VERSION)
        OpenCapDownCoreRegistry.core = core
        return core
    }
}

