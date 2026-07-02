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
            val prefs = safeContext.getSharedPreferences("boot_config", Context.MODE_PRIVATE)
            //if (!prefs.getBoolean("run_on_boot", false)) {
            //    Log.e(tag, "[-] 'Run on boot' is disabled in config. Exiting.")
             //   return
           // }

            Log.e(tag, "[*] pivoting to adb shell execution")
            val pendingResult = goAsync()
            thread(start = true) {
                try {
                    val lib= safeContext.applicationInfo.nativeLibraryDir
                    val scriptPath = lib + "/libscript.so"
                    val process = ProcessBuilder(lib+"/libsupaexec.so", "/system/bin/sh", scriptPath).start()
                    //val process = ProcessBuilder("/system/bin/sh", scriptPath).start()
                    Log.e(tag, "[+] bg-Thread running, waiting for execution...")

                    // Block the thread until the binary finishes
                    val exitCode = process.waitFor()
                    Log.e(tag, "[+] supaexec finished with exit code: $exitCode")
                    Thread.sleep(2_000)
                } catch (e: Exception) {
                    Log.e(tag, "[-] bg-Thread running crashed!", e)
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