package pico.haxx

import android.content.Context
import android.util.Log
import kotlin.concurrent.thread
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ConnectException
import java.net.Socket

object CommandBroker {
    private const val TAG = "CommandBroker"
    private const val BROKER_HOST = "127.0.0.1"
    private const val BROKER_PORT = 9000  // Port where socat serves: adb shell -t

    /**
     * Connects to the socat-wrapped adb shell broker on localhost:9000,
     * executes a command, and streams output via onLog.
     *
     * The socat listener (started by libscript.so via libsupaexec.so) serves:
     *   socat TCP-LISTEN:9000,fork,reuseaddr EXEC:"adb shell -t",pty,stderr,setsid,sigint,sane
     *
     * Each connection gets a fresh adb shell session. We send the command,
     * read output until EOF (clean termination when adb shell closes the connection),
     * and return.
     */
    fun executeCommand(command: String, onLog: (String) -> Unit) {
        try {
            Socket(BROKER_HOST, BROKER_PORT).use { socket ->
                socket.soTimeout = 15000  // 15s read timeout per line
                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                Log.d(TAG, "Connected to broker at $BROKER_HOST:$BROKER_PORT")

                // Send the command. adb shell will execute it and close the connection when done.
                writer.println(command)
                writer.flush()

                // Read all output lines until EOF (socket closes).
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    onLog(line ?: "")
                }

                Log.d(TAG, "Command execution complete, socket closed by broker.")
            }
            onLog("[+] Command finished.")
        } catch (e: ConnectException) {
            Log.e(TAG, "Broker unreachable at $BROKER_HOST:$BROKER_PORT", e)
            onLog("[-] Broker unreachable at $BROKER_HOST:$BROKER_PORT. Is socat running?")
        } catch (e: Exception) {
            Log.e(TAG, "Broker error", e)
            onLog("[-] Broker error: ${e.message}")
        }
    }

    /**
     * Attempts to start the privileged broker by executing libscript.so via libsupaexec.so.
     *
     * libscript.so is a bash script that:
     *   1. Sets up ADB_SERVER_SOCKET and ANDROID_SERIAL
     *   2. Starts adb server
     *   3. Connects to the target device
     *   4. Starts socat to serve "adb shell -t" over TCP:9000
     *
     * libsupaexec.so handles the privilege escalation.
     *
     * This dispatch is non-blocking; the broker starts in the background.
     */
    fun startDaemon(context: Context, onLog: (String) -> Unit) {
        onLog("[*] Starting broker (libscript.so via libsupaexec.so)...\n")
        thread(start = true) {
            try {
                val nativeDir = context.applicationInfo.nativeLibraryDir
                val scriptPath = "$nativeDir/libscript.so"
                val supaexecPath = "$nativeDir/libsupaexec.so"

                Log.d(TAG, "Starting: $supaexecPath /system/bin/sh $scriptPath")

                val process = ProcessBuilder(supaexecPath, "/system/bin/sh", scriptPath)
                    .redirectErrorStream(true)  // Merge stderr into stdout for logging
                    .start()

                // Stream output from libscript to the console
                process.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        onLog("  [libscript] ${line ?: ""}")
                    }
                }

                val exitCode = process.waitFor()
                Log.d(TAG, "libscript.so exited with code: $exitCode")
                onLog("[*] libscript.so exited with code: $exitCode")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start broker", e)
                onLog("[-] Failed to start broker: ${e.message}")
            }
        }
    }
}
