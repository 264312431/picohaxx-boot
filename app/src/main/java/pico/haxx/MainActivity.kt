package pico.haxx

import android.content.Context
import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.ScrollView
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var tvConsole: TextView
    private lateinit var btnStartDaemon: Button
    private lateinit var btnRun: Button
    private lateinit var btnCmdWhoami: Button
    private lateinit var btnCmdDmesg: Button
    private lateinit var cbRunOnBoot: CheckBox
    private lateinit var scrollView: ScrollView
    private lateinit var ansiParser: AnsiColorParser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvConsole = findViewById(R.id.tvConsole)
        btnStartDaemon = findViewById(R.id.btnStartDaemon)
        btnRun = findViewById(R.id.btnRun)
        btnCmdWhoami = findViewById(R.id.btnCmdWhoami)
        btnCmdDmesg = findViewById(R.id.btnCmdDmesg)
        cbRunOnBoot = findViewById(R.id.cbRunOnBoot)
        scrollView = findViewById(R.id.scrollView)
        ansiParser = AnsiColorParser()

        // Important: Save config to Device Protected Storage so the BootReceiver
        // can read it during Direct Boot (before the user enters their PIN)
        val deviceProtectedContext = createDeviceProtectedStorageContext()
        val prefs = deviceProtectedContext.getSharedPreferences("boot_config", Context.MODE_PRIVATE)

        // Load saved state
        cbRunOnBoot.isChecked = prefs.getBoolean("run_on_boot", false)

        // Save state on toggle
        cbRunOnBoot.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("run_on_boot", isChecked).apply()
        }

        // Start the privileged broker
        btnStartDaemon.setOnClickListener {
            appendToConsole("[*] Starting daemon via libscript...\n")
            CommandBroker.startDaemon(this) { appendToConsole(it) }
        }

        // Run the payload binary (libpicohaxx.so)
        btnRun.setOnClickListener {
            dispatchPayload("libpicohaxx.so")
        }

        btnCmdWhoami.setOnClickListener {
            dispatchCommand("id")
        }

        btnCmdDmesg.setOnClickListener {
            dispatchCommand("dmesg | tail -n 20")
        }
    }

    private fun dispatchCommand(cmd: String) {
        appendToConsole("\n--- Executing: $cmd ---\n")
        toggleButtons(false)

        thread(start = true) {
            CommandBroker.executeCommand(cmd) { line ->
                appendToConsole(line)
            }

            runOnUiThread {
                toggleButtons(true)
            }
        }
    }

    private fun dispatchPayload(payloadName: String) {
        appendToConsole("\n--- Running payload: $payloadName ---\n")
        toggleButtons(false)

        thread(start = true) {
            CommandBroker.executePayload(this, payloadName) { line ->
                appendToConsole(line)
            }

            runOnUiThread {
                toggleButtons(true)
            }
        }
    }

    private fun appendToConsole(text: String) {
        val styled = ansiParser.parse(text + "\n")
        runOnUiThread {
            tvConsole.append(styled)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun toggleButtons(enabled: Boolean) {
        btnStartDaemon.isEnabled = enabled
        btnRun.isEnabled = enabled
        btnCmdWhoami.isEnabled = enabled
        btnCmdDmesg.isEnabled = enabled
    }
}
