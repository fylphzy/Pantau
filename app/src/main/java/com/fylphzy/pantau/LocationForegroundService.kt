package com.fylphzy.pantau

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.google.android.gms.location.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LocationForegroundService : Service() {
    companion object {
        const val TAG = "LocationForegroundSvc"
        const val EXTRA_USERNAME = "extra_username"
        private const val CHANNEL_ID = "location_channel"
        private const val NOTIF_ID = 1001

        private const val PREFS_NAME = "trc_prefs"
        private const val KEY_RUNNING = "location_service_running"
        private const val KEY_LAST_HEARTBEAT = "location_service_last_heartbeat"
        private const val KEY_LAST_STOP = "location_service_last_stop"

        private const val DEBOUNCE_MS = 5_000L
        private const val HEARTBEAT_TIMEOUT_MS = 15_000L

        @Volatile
        var isRunning = false
            private set
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private var username: String = ""

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(3_000L)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .build()

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        username = intent?.getStringExtra(EXTRA_USERNAME) ?: ""

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lastStop = prefs.getLong(KEY_LAST_STOP, 0L)
        val now = System.currentTimeMillis()

        if (now - lastStop < DEBOUNCE_MS) {
            Log.i(TAG, "Start ignored due to debounce (last stop ${now - lastStop}ms ago)")
            prefs.edit { putBoolean(KEY_RUNNING, false) }
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }

        val persistedRunning = prefs.getBoolean(KEY_RUNNING, false)
        val lastHeartbeat = prefs.getLong(KEY_LAST_HEARTBEAT, 0L)
        if (persistedRunning && now - lastHeartbeat < HEARTBEAT_TIMEOUT_MS) {
            Log.i(TAG, "Service reported running by prefs and heartbeat recent. Ignoring start.")
            isRunning = true
            return START_STICKY
        }

        if (!isRunning) {
            isRunning = true
            prefs.edit { putBoolean(KEY_RUNNING, true) }
            startForeground(NOTIF_ID, buildNotification())
            startLocationUpdates()
            Log.i(TAG, "Service started for user=$username")
        } else {
            Log.i(TAG, "Service already running (volatile). Start request ignored.")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopLocationUpdates()
        isRunning = false
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit {
            putBoolean(KEY_RUNNING, false)
            putLong(KEY_LAST_STOP, System.currentTimeMillis())
        }
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        try {
            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (ex: SecurityException) {
            Log.e(TAG, "startLocationUpdates: missing permission", ex)
        } catch (ex: Exception) {
            Log.e(TAG, "startLocationUpdates failed", ex)
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (ex: Exception) {
            Log.e(TAG, "stopLocationUpdates failed", ex)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc: Location = result.lastLocation ?: return
            val lat = loc.latitude
            val lon = loc.longitude

            // Validasi numerik
            if (!lat.isFinite() || !lon.isFinite()) {
                Log.w(TAG, "Invalid location values. lat=$lat lon=$lon. Skip send.")
                return
            }

            // update heartbeat
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit { putLong(KEY_LAST_HEARTBEAT, System.currentTimeMillis()) }

            sendLocationToServer(lat, lon)
        }
    }

    private fun sendLocationToServer(lat: Double, lon: Double) {
        if (username.isBlank()) return

        val map = mutableMapOf<String, Any?>(
            "username" to username,
            "la" to lat,
            "lo" to lon
        )
        map.entries.removeIf { it.value == null }

        RetrofitClient.apiService.updateData(map)
            .enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (!response.isSuccessful) {
                        Log.w(TAG, "sendLocationToServer failed code=${response.code()}")
                    }
                }

                override fun onFailure(call: Call<Void>, t: Throwable) {
                    Log.w(TAG, "sendLocationToServer onFailure: ${t.message}")
                }
            })
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val chan = NotificationChannel(CHANNEL_ID, "Location", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(chan)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TRC Pantau")
            .setContentText("Mengirim lokasi...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }
}
