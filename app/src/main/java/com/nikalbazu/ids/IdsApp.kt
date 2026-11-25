package com.nikalbazu.ids

import android.app.Application
import com.nikalbazu.ids.core.Community
import com.nikalbazu.ids.core.ErrorReporter
import com.nikalbazu.ids.core.Store
import com.nikalbazu.ids.priv.Device
import com.nikalbazu.ids.priv.Helper

class IdsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ErrorReporter.install(this)
        Store.init(this)
        Helper.init(this)
        Device.init(this)
        Community.init(this)
    }
}
