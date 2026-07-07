package com.opencapdown.app

import android.app.Application
import com.opencapdown.core.OpenCapDownCore
import com.opencapdown.core.OpenCapDownCoreFactory

class OpenCapDownApp : Application() {
    lateinit var core: OpenCapDownCore
        private set

    override fun onCreate() {
        super.onCreate()
        core = OpenCapDownCoreFactory.create(this)
    }
}
