package com.lenerd.spotifyplus

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.lenerd.spotifyplus.manager.bridge.BridgeService
import com.lenerd.spotifyplus.manager.scripting.ScriptManager
import com.lenerd.spotifyplus.manager.ui.SpotifyPlusApp
import com.lenerd.spotifyplus.manager.ui.theme.SpotifyPlusTheme
import com.lenerd.spotifyplus.manager.util.SpotifyCheck
import com.lenerd.spotifyplus.manager.viewmodel.ManagerViewModel
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: ManagerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                val prefs = service.getRemotePreferences("SpotifyPlus")
                SettingsSync.syncLocalToRemote(this@MainActivity, prefs)
                SettingsSync.syncRemoteToLocal(this@MainActivity, prefs)
            }

            override fun onServiceDied(p0: XposedService) { }
        })

        viewModel = ViewModelProvider(this)[ManagerViewModel::class.java]
        viewModel.checkForUpdates();

        val spotifyCheck = SpotifyCheck.check(this)
        viewModel.setSpotifyInstalled(spotifyCheck.installed, spotifyCheck.versionName)

        setContent {
            SpotifyPlusTheme {
                Surface(modifier = Modifier) {
                    SpotifyPlusApp(viewModel = viewModel, context = this)
                }
            }
        }

//        val scriptManager = ScriptManager(this)
//        scriptManager.start()

//        BridgeService()
//
//        Handler(Looper.getMainLooper()).postDelayed({
//            scriptManager.testPing()
//        }, 1000)

//        if(!nodeStarted) {
//            nodeStarted = true
//            Thread(Runnable {
//                run {
//                    startNodeWithArguments(arrayOf("node", "-e", "var http = require('http'); " +
//                            "var versions_server = http.createServer( (request, response) => { " +
//                            "  response.end('Versions: ' + JSON.stringify(process.versions)); " +
//                            "}); " +
//                            "versions_server.listen(3000);"))
//                }
//            }).start()
//        }
    }
}