package com.heynex

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.heynex.databinding.ActivityMainBinding

/**
 * Main activity displaying status, command history, and quick actions.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var commandHistoryAdapter: CommandHistoryAdapter

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.INTERNET,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.READ_CALENDAR
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            startAssistant()
        } else {
            Toast.makeText(this, "Beberapa izin diperlukan untuk Hey Nex", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        commandHistoryAdapter = CommandHistoryAdapter()
        binding.recyclerCommandHistory.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = commandHistoryAdapter
        }

        CommandHistoryRepository.commands.observe(this) { commands ->
            commandHistoryAdapter.submitList(commands)
        }

        binding.buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.buttonStartService.setOnClickListener {
            checkPermissionsAndStart()
        }

        binding.buttonStopService.setOnClickListener {
            VoiceAssistantService.stop(this)
            updateStatus(false)
        }

        binding.buttonGrantOverlay.setOnClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }

        binding.buttonNotificationAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionIfNeeded()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus(isServiceRunning())
    }

    private fun isServiceRunning(): Boolean = VoiceAssistantService.isRunning

    private fun checkPermissionsAndStart() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToRequest.isEmpty()) {
            startAssistant()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun startAssistant() {
        val settings = AppSettings(this)
        if (settings.groqApiKey.isNullOrBlank()) {
            Toast.makeText(this, "Masukkan kunci API Groq di pengaturan", Toast.LENGTH_LONG).show()
        }
        VoiceAssistantService.start(this)
        updateStatus(true)
    }

    private fun updateStatus(running: Boolean) {
        binding.textStatus.text = if (running) "Status: Aktif" else "Status: Tidak aktif"
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }
}
