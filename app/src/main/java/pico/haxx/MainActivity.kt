package pico.haxx

import android.content.Context
import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.ScrollView
import kotlin.concurrent.thread

private lateinit var tvConsole: TextView
private lateinit var btnRunBinary: Button
private lateinit var cbRunOnBoot: CheckBox


class MainActivity : Activity() {

    private lateinit var tvConsole: TextView
    private lateinit var btnRunBinary: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvConsole = findViewById(R.id.tvConsole)
        btnRunBinary = findViewById(R.id.btnRunBinary)
        cbRunOnBoot = findViewById(R.id.cbRunOnBoot)
        val scrollView = tvConsole.parent as ScrollView

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

        btnRunBinary.setOnClickListener {
            btnRunBinary.isEnabled = false
            tvConsole.append("\n--- Starting manual execution ---\n")

            // Instantiate a fresh parser for this execution
            val ansiParser = AnsiColorParser()

            thread(start = true) {
                BinaryRunner.execute(this) { line ->

                    // Parse the line (and append a newline) on the background thread!
                    // This prevents regex parsing from bogging down the UI.
                    val styledLine = ansiParser.parse(line + "\n")

                    runOnUiThread {
                        // TextView.append natively supports Spannables
                        tvConsole.append(styledLine)

                        // Auto-scroll to bottom
                        scrollView.post {
                            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                        }
                    }
                }

                runOnUiThread {
                    btnRunBinary.isEnabled = true
                }
            }
        }
    }


    object BinaryRunner {
        // Execute a binary and route every line of output to the onLog callback
        fun execute(context: Context, onLog: (String) -> Unit): Int {
            return try {
                val scriptPath = context.applicationInfo.nativeLibraryDir + "/libscript.so"
                // val scriptPath = context.applicationInfo.nativeLibraryDir + "/libpicohaxx.so"
                val process = ProcessBuilder("/system/bin/sh", scriptPath)
                    //val process = ProcessBuilder("/system/bin/sh", "-c", "export _LOGWRAPPED=0;source " +scriptPath)
                    // val process = ProcessBuilder(scriptPath)
                    .redirectErrorStream(true) // Merges stderr into stdout
                    .start()

                // Stream the output live
                process.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        onLog(line ?: "")
                    }
                }

                val exitCode = process.waitFor()
                onLog("[*] Process exited with code: $exitCode")

                if (exitCode == 0) {
                    onLog("[+] SUCCESS: Binary finished!")
                } else {
                    onLog("[-] FAILURE: Binary returned non-zero exit code.")
                }
                exitCode
            } catch (e: Exception) {
                onLog("[-] EXCEPTION: ${e.message}")
                -1
            }
        }
    }
}