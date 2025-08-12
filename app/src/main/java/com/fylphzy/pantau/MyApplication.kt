package com.fylphzy.pantau

import android.app.Application
import android.os.StrictMode
import android.os.Build

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .apply { if (Build.VERSION.SDK_INT >= 30) detectActivityLeaks() }
                .build()
        )
    }
}
