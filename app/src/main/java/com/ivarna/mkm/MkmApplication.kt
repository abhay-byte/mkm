package com.ivarna.mkm

import android.app.Application
import com.ivarna.mkm.shell.ShizukuManager
import com.topjohnwu.superuser.Shell

class MkmApplication : Application() {
    companion object {
        init {
            // Set settings before the main shell can be created
            Shell.enableVerboseLogging = true // Force verbose logging for now
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(10)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Shizuku for v1.1
        ShizukuManager.init(this)
    }
}
