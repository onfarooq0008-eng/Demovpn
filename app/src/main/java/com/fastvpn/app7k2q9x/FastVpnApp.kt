package com.fastvpn.app7k2q9x

import android.app.Application
import com.fastvpn.app7k2q9x.data.ServerRepository
import com.fastvpn.app7k2q9x.data.ServerStore
import com.fastvpn.app7k2q9x.data.Settings
import com.fastvpn.app7k2q9x.vpn.VpnController
import com.fastvpn.app7k2q9x.worker.RefreshWorker

class FastVpnApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        ServerStore.init(this)
        ServerRepository.init(this)
        VpnController.init(this)
        RefreshWorker.schedule(this)
    }
}
