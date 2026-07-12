package io.ddys.cloudstream

import android.app.Activity
import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DdysPlugin : Plugin() {
    private var activity: Activity? = null

    override fun load(context: Context) {
        activity = context as? Activity
        val settings = DdysSettings(context)
        registerMainAPI(DdysProvider(settings))

        openSettings = {
            activity?.let { DdysSettingsDialog.show(it, settings) }
        }
    }
}
