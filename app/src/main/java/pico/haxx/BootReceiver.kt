package pico.haxx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import kotlin.concurrent.thread

val tag="pico.haxx.boot"

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.e(tag, "======================================================")
        Log.e(tag, "🔥 RECEIVER Action: ${intent.action}")
        Log.e(tag, "======================================================")
        try {
            // still in LOCKED-State?
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val isUnlocked = userManager.isUserUnlocked
            Log.e(tag, "[*] FBE Status - Is User Unlocked? : $isUnlocked")

            val safeContext = if (!isUnlocked) {
                Log.e(tag, "[!] switch to DeviceProtectedStorageContext!")
                context.createDeviceProtectedStorageContext()
            } else {
                context
            }

            // Gate the execution using our config from DE storage
            // Default to TRUE on fresh install (data cleared)
            val prefs = safeContext.getSharedPreferences("boot_config", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("run_on_boot", true)) {
                Log.e(tag, "[-] 'Run on boot' is disabled in config. Exiting.")
                return
            }

            Log.e(tag, "[*] 'Run on boot' enabled. Starting broker and payload...")
            val pendingResult = goAsync()
            thread(start = true) {
                try {
                    // Start the broker daemon (socat on port 9000)
                    Log.e(tag, "[*] Starting daemon via libscript...")
                    CommandBroker.startDaemon(safeContext) { line ->
                        Log.e(tag, "[daemon] $line")
                    }

                    // Give the broker time to start
                    Thread.sleep(3_000)

                    // Execute the payload via the broker
                    Log.e(tag, "[*] Executing payload (libpicohaxx.so) via broker...")
                    CommandBroker.executePayload(safeContext, "libpicohaxx.so") { line ->
                        Log.e(tag, "[payload] $line")
                    }

                    Log.e(tag, "[+] Payload execution complete.")
                } catch (e: Exception) {
                    Log.e(tag, "[-] bg-Thread crashed!", e)
                } finally {
                    // NOW we tell Android we are done.
                    pendingResult.finish()
                    Log.e(tag, "[*] pendingResult.finish() called.")
                }
            }
        } catch (t: Throwable) {
            Log.e(tag, "💥 FATAL CRASH IN BOOTRECEIVER!", t)
        }
    }
}
