package com.freelybase.kotlin

import android.app.Application
import io.freelybase.kotlin.FreelyBase

class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        FreelyBase.initialize(
            context = this,
            appId = "14f93c08ef074681b17d968f450074ed",
        )
    }
}
