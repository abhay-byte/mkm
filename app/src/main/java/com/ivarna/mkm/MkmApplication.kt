package com.ivarna.mkm

import android.app.Application
import com.ivarna.mkm.shell.ShizukuManager
import com.ivarna.mkm.util.AppVisibilityMonitor
import com.topjohnwu.superuser.Shell

class MkmApplication : Application() {
    companion object {
        init {
            // Set settings before the main shell can be created
            Shell.enableVerboseLogging = true
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR)
                    // Some rooted Android builds omit `su` from an app's PATH.
                    // KernelSU exposes the executable at this stable absolute path.
                    .setCommands("/system/bin/su", "-c", "sh")
                    .setTimeout(10)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        ShizukuManager.init(this)
        AppVisibilityMonitor.init(this)
    }
}
