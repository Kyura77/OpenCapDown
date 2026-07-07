package com.opencapdown.app

import android.app.Application
import android.os.Build
import android.webkit.WebView
import com.opencapdown.core.OpenCapDownCore
import com.opencapdown.core.OpenCapDownCoreFactory

class OpenCapDownApp : Application() {
    lateinit var core: OpenCapDownCore
        private set

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        core = OpenCapDownCoreFactory.create(this)
    }
}
