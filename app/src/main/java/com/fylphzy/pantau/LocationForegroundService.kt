package com.fylphzy.pantau

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LocationForegroundService : Service() {

    private val tag = "LocationFgService"

    private lateinit var fusedClient: com.google.android.gms.location.FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var username: String = ""

    private lateinit var prefs: SharedPreferences

    companion object {
        @JvmField
        var isRunning: Boolean = false

        const val EXTRA_USERNAME = "extra_username"

        // Shared prefs keys (must match MainActivity)
        private const val PREFS_NAME = "trc_prefs"
        private const val KEY_RUNNING = "location_service_running"
        private const val KEY_LAST_HEARTBEAT = "location_service_last_heartbeat"

        // Keys shared with MainActivity for emergency/conf status
        private const val KEY_EMR_ACTIVE = "emr_active"
        private const val KEY_CONF_STATUS = "conf_status"

        // Notification
        private const val CHANNEL_ID = "pantau_location_channel"
        private const val CHANNEL_NAME = "Pantau Location"
        private const val NOTIF_ID = 1423

        // Location intervals (1 second heartbeat as requested)
        private const val UPDATE_INTERVAL_MS = 1_000L
        private const val FASTEST_INTERVAL_MS = 1_000L
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "onCreate")
        isRunning = true

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        buildLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "onStartCommand")
        isRunning = true

        username = intent?.getStringExtra(EXTRA_USERNAME) ?: ""
        // Start foreground (safe even if MainActivity already started it)
        startForeground(NOTIF_ID, createNotification())

        // mark running in prefs
        prefs.edit {
            putBoolean(KEY_RUNNING, true)
            putLong(KEY_LAST_HEARTBEAT, System.currentTimeMillis())
        }

        startLocationUpdates()

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(tag, "onDestroy")
        stopLocationUpdates()
        isRunning = false

        prefs.edit {
            putBoolean(KEY_RUNNING, false)
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------
    // Location handling
    // -------------------------
    private fun buildLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc: Location? = result.lastLocation
                if (loc != null) {
                    val la = loc.latitude
                    val lo = loc.longitude
                    Log.d(tag, "Location received: $la, $lo")

                    // validate numeric values
                    if (!la.isFinite() || !lo.isFinite()) {
                        Log.w(tag, "Invalid location values. lat=$la lon=$lo. Skip send.")
                        return
                    }

                    prefs.edit {
                        putLong(KEY_LAST_HEARTBEAT, System.currentTimeMillis())
                        putBoolean(KEY_RUNNING, true)
                    }

                    // Only send location if emergency active AND conf_status == 0
                    val emrActive = prefs.getBoolean(KEY_EMR_ACTIVE, false)
                    val confStatus = prefs.getInt(KEY_CONF_STATUS, 0)

                    if (emrActive && confStatus == 0) {
                        sendLocationToServer(la, lo)
                    } else {
                        Log.d(tag, "Skip sending location. emrActive=$emrActive confStatus=$confStatus")
                    }
                } else {
                    Log.w(tag, "LocationResult had null lastLocation")
                }
            }
        }
    }

    private fun startLocationUpdates() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!fine) {
            Log.w(tag, "Missing ACCESS_FINE_LOCATION permission — cannot start location updates")
            stopSelf()
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setMaxUpdateDelayMillis(UPDATE_INTERVAL_MS)
            .build()

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            Log.d(tag, "Requested location updates (interval ${UPDATE_INTERVAL_MS}ms)")
        } catch (e: SecurityException) {
            Log.e(tag, "SecurityException requesting location updates: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(tag, "Exception requesting location updates: ${e.message}", e)
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedClient.removeLocationUpdates(locationCallback)
            Log.d(tag, "Removed location updates")
        } catch (e: Exception) {
            Log.e(tag, "Error removing location updates: ${e.message}", e)
        }
    }

    // -------------------------
    // Networking
    // -------------------------
    /**
     * Will send username, la, lo and emr=1 (service never sends emr_desc or conf_status).
     * This method is called only when prefs indicate emrActive == true and conf_status == 0.
     */
    private fun sendLocationToServer(la: Double, lo: Double) {
        if (username.isBlank()) {
            Log.w(tag, "No username provided, skipping server update")
            return
        }

        try {
            val map = mutableMapOf<String, Any?>(
                "username" to username,
                "la" to la,
                "lo" to lo,
                "emr" to 1 // include emr=1 to indicate this heartbeat relates to an active emergency
            )

            // remove nulls just in case
            map.entries.removeIf { it.value == null }

            RetrofitClient.apiService.updateData(map)
                .enqueue(object : Callback<BasicResponse> {
                    override fun onResponse(call: Call<BasicResponse>, response: Response<BasicResponse>) {
                        if (response.isSuccessful) {
                            Log.d(tag, "Location update success: ${response.body()?.message ?: "OK"}")
                        } else {
                            Log.w(tag, "Location update failed http:${response.code()}")
                        }
                    }

                    override fun onFailure(call: Call<BasicResponse>, t: Throwable) {
                        Log.e(tag, "Location update failure: ${t.message}", t)
                    }
                })
        } catch (e: Exception) {
            Log.e(tag, "Exception when sending location to server: ${e.message}", e)
        }
    }

    // -------------------------
    // Notification
    // -------------------------
    private fun createNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)!!
        val chan = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(chan)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("username", username)
        }

        val pendingIntentFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pending = PendingIntent.getActivity(this, 0, intent, pendingIntentFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (username.isBlank()) "Pantau — Location active" else "Pantau — $username (tracking)")
            .setContentText("Mengirim lokasi ke server")
            .setSmallIcon(R.drawable.ic_location)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
