package com.fylphzy.pantau

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var loginButton: Button
    private val tagLog = "LoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        usernameEditText = findViewById(R.id.username)
        loginButton = findViewById(R.id.loginBtn)

        // cek login tersimpan. first() dipanggil di coroutine; import kotlinx.coroutines.flow.first diperlukan.
        lifecycleScope.launch {
            val logged = DataStoreManager.isLoggedInFlow(applicationContext).first()
            if (logged) {
                val savedUser: String? = DataStoreManager.usernameFlow(applicationContext).first()
                if (!savedUser.isNullOrBlank()) {
                    // pastikan savedUser adalah String (nullable diterima oleh putExtra)
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java).apply {
                        putExtra("username", savedUser)
                    })
                    finish()
                    return@launch
                }
            }
        }

        loginButton.setOnClickListener {
            val inputUsername = usernameEditText.text.toString().trim()
            if (inputUsername.isNotEmpty()) {
                checkUser(inputUsername)
            } else {
                Toast.makeText(this, "Username tidak boleh kosong", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkUser(inputUsername: String) {
        RetrofitClient.apiService.getUser(inputUsername).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    val list = apiResponse?.data ?: emptyList()
                    val found = list.any { it.username == inputUsername }

                    val statusOk = apiResponse?.status?.equals("ok", true) == true ||
                            apiResponse?.status?.equals("success", true) == true

                    if (statusOk && found) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            DataStoreManager.saveLogin(applicationContext, inputUsername)
                            withContext(Dispatchers.Main) {
                                startActivity(Intent(this@LoginActivity, MainActivity::class.java).apply {
                                    putExtra("username", inputUsername)
                                })
                                finish()
                            }
                        }
                    } else {
                        Toast.makeText(this@LoginActivity, "Username tidak ditemukan", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@LoginActivity, "Gagal memeriksa pengguna", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                Log.e(tagLog, "Koneksi gagal: ${t.message}", t)
                Toast.makeText(this@LoginActivity, "Koneksi gagal", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
