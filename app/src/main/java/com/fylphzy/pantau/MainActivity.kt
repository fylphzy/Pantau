package com.fylphzy.pantau

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val tag = "MainActivity"

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var akurasiBtn: Button
    private lateinit var emergencyBtn: Button
    private lateinit var logoutBtn: Button
    private lateinit var indicatorKonfirmasi: ImageView
    private lateinit var valueLatitude: TextView
    private lateinit var valueLongitude: TextView
    private lateinit var cancelemer: TextView
    private lateinit var greetingText: TextView

    private var username: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private val pollingIntervalMillis = 15_000L
    private var pollingRunnable: Runnable? = null

    private var confResetInProgress = false
    private var userRequestedRefresh = false

    companion object {
        private const val REQ_FINE_LOCATION = 1001
        private const val REQ_BACKGROUND_LOCATION = 1002

        // prefs mirror
        private const val PREFS_NAME = "trc_prefs"
        private const val KEY_RUNNING = "location_service_running"
        private const val KEY_LAST_HEARTBEAT = "location_service_last_heartbeat"
        private const val HEARTBEAT_TIMEOUT_MS = 15_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        akurasiBtn = findViewById(R.id.akurasiBtn)
        emergencyBtn = findViewById(R.id.emergencyBtn)
        logoutBtn = findViewById(R.id.logoutBtn)
        indicatorKonfirmasi = findViewById(R.id.indicatorKonfirmasi)
        valueLatitude = findViewById(R.id.valueLatitude)
        valueLongitude = findViewById(R.id.valueLongitude)
        cancelemer = findViewById(R.id.cancelemer)
        greetingText = findViewById(R.id.greetingText)

        NotificationHelper.createNotificationChannelIfNeeded(applicationContext)

        swipeRefresh.setOnRefreshListener {
            userRequestedRefresh = true
            checkConfirmationStatus()
        }

        username = intent.getStringExtra("username") ?: ""

        if (username.isNotBlank()) {
            greetingText.text = username
            startPollingStatus()
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                val saved = DataStoreManager.getUsernameOnce(applicationContext)
                withContext(Dispatchers.Main) {
                    username = saved ?: ""
                    if (username.isNotBlank()) {
                        greetingText.text = username
                        startPollingStatus()
                    } else {
                        Log.w(tag, "Username kosong, user harus login ulang")
                    }
                }
            }
        }

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        startPollingStatus()
    }

    override fun onPause() {
        super.onPause()
        stopPollingStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPollingStatus()
    }

    private fun setupListeners() {
        akurasiBtn.setOnClickListener {
            Toast.makeText(this, getString(R.string.akurasi_requested), Toast.LENGTH_SHORT).show()
            checkConfirmationStatus()
        }

        emergencyBtn.setOnClickListener {
            showEmergencyDialog()
        }

        cancelemer.setOnClickListener {
            sendEmergency(0, null)
            stopLocationServiceIfRunning()
        }

        logoutBtn.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                DataStoreManager.clearLogin(applicationContext)
                withContext(Dispatchers.Main) {
                    stopPollingStatus()
                    Toast.makeText(this@MainActivity, getString(R.string.logged_out), Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    finish()
                }
            }
        }
    }

    private fun showEmergencyDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_emr_description)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        val editDesc = dialog.findViewById<EditText>(R.id.editTextEmrDesc)
        val btnOk = dialog.findViewById<Button>(R.id.btnOk)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)

        btnOk.setOnClickListener {
            val desc = editDesc.text.toString().trim()
            if (desc.isEmpty()) {
                Toast.makeText(this, "Deskripsi tidak boleh kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendEmergency(1, desc)
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            sendEmergency(0, null)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun startPollingStatus() {
        if (pollingRunnable != null) return
        pollingRunnable = object : Runnable {
            override fun run() {
                checkConfirmationStatus()
                handler.postDelayed(this, pollingIntervalMillis)
            }
        }
        handler.post(pollingRunnable!!)
    }

    private fun stopPollingStatus() {
        pollingRunnable?.let { handler.removeCallbacks(it) }
        pollingRunnable = null
    }

    private fun checkConfirmationStatus() {
        if (username.isBlank()) {
            Log.w(tag, "checkConfirmationStatus dipanggil tetapi username kosong")
            if (userRequestedRefresh) {
                runOnUiThread { swipeRefresh.isRefreshing = false }
                userRequestedRefresh = false
            }
            return
        }

        RetrofitClient.apiService.getUser(username)
            .enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    if (userRequestedRefresh) {
                        runOnUiThread { swipeRefresh.isRefreshing = false }
                        userRequestedRefresh = false
                    }

                    if (!response.isSuccessful) {
                        Log.e(tag, "checkConfirmationStatus: not successful code=${response.code()}")
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.status_check_failed_server),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return
                    }

                    val body = response.body()
                    if (body == null) {
                        Log.w(tag, "checkConfirmationStatus: response body null")
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.status_check_failed_empty),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return
                    }

                    // Important: move parsing/formatting to background to avoid jank
                    lifecycleScope.launch(Dispatchers.Default) {
                        val user = body.data.firstOrNull()

                        val emrInt = user?.emr ?: 0
                        val confStatusInt = user?.confStatus ?: 0
                        val la = user?.la ?: 0.0
                        val lo = user?.lo ?: 0.0

                        withContext(Dispatchers.Main) {
                            // UI updates only on main thread
                            valueLatitude.text = String.format(Locale.getDefault(), "%.6f", la)
                            valueLongitude.text = String.format(Locale.getDefault(), "%.6f", lo)
                            indicatorKonfirmasi.visibility = View.VISIBLE
                            indicatorKonfirmasi.isSelected = (confStatusInt == 1)

                            if (emrInt == 0 && confStatusInt == 1) {
                                if (!confResetInProgress) {
                                    confResetInProgress = true
                                    resetConfStatusToZeroOnServer()
                                }
                            }

                            if (emrInt == 1) {
                                ensureLocationPermissionsAndStartServiceIfNeeded()
                            } else {
                                stopLocationServiceIfRunning()
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    if (userRequestedRefresh) {
                        runOnUiThread { swipeRefresh.isRefreshing = false }
                        userRequestedRefresh = false
                    }
                    Log.e(tag, "checkConfirmationStatus onFailure: ${t.message}", t)
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.status_check_failed_network),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })
    }

    private fun resetConfStatusToZeroOnServer() {
        val map = mutableMapOf<String, Any?>(
            "username" to username,
            "conf_status" to 0
        )
        map.entries.removeIf { it.value == null }

        RetrofitClient.apiService.updateData(map).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                confResetInProgress = false
                if (response.isSuccessful) {
                    Log.d(tag, "resetConfStatusToZeroOnServer: success")
                    runOnUiThread {
                        indicatorKonfirmasi.visibility = View.VISIBLE
                        indicatorKonfirmasi.isSelected = false
                    }
                } else {
                    Log.e(tag, "resetConfStatusToZeroOnServer: failed code=${response.code()}")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                confResetInProgress = false
                Log.e(tag, "resetConfStatusToZeroOnServer onFailure: ${t.message}", t)
            }
        })
    }

    private fun sendEmergency(emrValue: Int, emrDesc: String?) {
        if (username.isBlank()) {
            Toast.makeText(this, "Username tidak tersedia", Toast.LENGTH_SHORT).show()
            return
        }

        val map = mutableMapOf<String, Any?>(
            "username" to username,
            "emr" to emrValue
        )
        if (emrValue == 1 && !emrDesc.isNullOrBlank()) {
            map["emr_desc"] = emrDesc
        }
        map.entries.removeIf { it.value == null }

        RetrofitClient.apiService.updateData(map).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    if (emrValue == 1) {
                        Toast.makeText(this@MainActivity, getString(R.string.emergency_sent), Toast.LENGTH_SHORT).show()
                        ensureLocationPermissionsAndStartServiceIfNeeded()
                    } else {
                        Toast.makeText(this@MainActivity, getString(R.string.emergency_cancelled_toast), Toast.LENGTH_SHORT).show()
                        stopLocationServiceIfRunning()
                    }
                    checkConfirmationStatus()
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.emergency_failed_server), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e(tag, "sendEmergency onFailure: ${t.message}", t)
                Toast.makeText(this@MainActivity, getString(R.string.emergency_failed_network), Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun ensureLocationPermissionsAndStartServiceIfNeeded() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_FINE_LOCATION)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasBackground = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasBackground) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ_BACKGROUND_LOCATION)
                return
            }
        }

        startLocationService()
    }

    private fun startLocationService() {
        if (username.isBlank()) {
            Log.w(tag, "Tidak bisa start LocationForegroundService, username kosong")
            return
        }

        if (LocationForegroundService.isRunning) {
            Log.i(tag, "startLocationService: service sudah berjalan (volatile), skip start")
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val persistedRunning = prefs.getBoolean(KEY_RUNNING, false)
        val lastHeartbeat = prefs.getLong(KEY_LAST_HEARTBEAT, 0L)
        val now = System.currentTimeMillis()
        if (persistedRunning && now - lastHeartbeat < HEARTBEAT_TIMEOUT_MS) {
            Log.i(tag, "startLocationService: prefs indicate service running and heartbeat recent, skip start")
            return
        }

        if (!hasFineLocationPermission()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_FINE_LOCATION)
            return
        }

        if (!hasBackgroundLocationPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ_BACKGROUND_LOCATION)
            }
            return
        }

        try {
            val intent = Intent(this, LocationForegroundService::class.java).apply {
                putExtra(LocationForegroundService.EXTRA_USERNAME, username)
            }
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            Log.e(tag, "Gagal memulai LocationForegroundService: ${e.message}", e)
        }
    }

    private fun stopLocationServiceIfRunning() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val persistedRunning = prefs.getBoolean(KEY_RUNNING, false)

            if (!LocationForegroundService.isRunning && !persistedRunning) {
                Log.i(tag, "stopLocationServiceIfRunning: service tidak berjalan, skip stop")
                return
            }

            val intent = Intent(this, LocationForegroundService::class.java)
            stopService(intent)
        } catch (e: Exception) {
            Log.e(tag, "Gagal menghentikan LocationForegroundService: ${e.message}", e)
        }
    }

    private fun hasFineLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_FINE_LOCATION -> {
                val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                if (granted) startLocationService()
            }
            REQ_BACKGROUND_LOCATION -> {
                val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                if (granted) startLocationService()
            }
        }
    }
}
