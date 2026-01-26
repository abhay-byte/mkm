package com.ivarna.mkm.shell

import com.topjohnwu.superuser.Shell
// Shizuku support disabled - uncomment when dependencies are available
// import rikka.shizuku.Shizuku
// import rikka.shizuku.ShizukuRemoteProcess
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object ShellManager {

    enum class Mode {
        SHIZUKU,
        ROOT,
        DEFAULT
    }

    /**
     * Executes a command and returns the result.
     * Prioritizes Shizuku, then Root, then local shell.
     */
    fun exec(command: String): CommandResult {
        return when {
            ShizukuHelper.isAvailable() && ShizukuHelper.hasPermission() -> {
                execShizuku(command)
            }
            Shell.getShell().isRoot -> {
                execRoot(command)
            }
            else -> {
                execLocal(command)
            }
        }
    }

    private fun execShizuku(command: String): CommandResult {
        // Shizuku support disabled - uncomment when dependencies are available
        return CommandResult(-1, "", "Shizuku not available")
        /* return try {
            // Use the public API through IShizukuService
            val iRemoteProcess = Shizuku.getService().newProcess(
                arrayOf("sh", "-c", command),
                null,
                null
            )
            
            // Wrap in ShizukuRemoteProcess
            val process = ShizukuRemoteProcess(iRemoteProcess)
            val output = StringBuilder()
            val error = StringBuilder()
            
            val outReader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            
            var line: String?
            while (outReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            while (errReader.readLine().also { line = it } != null) {
                error.append(line).append("\n")
            }
            
            process.waitFor()
            CommandResult(process.exitValue(), output.toString().trim(), error.toString().trim())
        } catch (e: Exception) {
            CommandResult(-1, "", e.message ?: "Unknown Shizuku error")
        } */
    }

    private fun execRoot(command: String): CommandResult {
        val result = Shell.cmd(command).exec()
        return CommandResult(
            result.code,
            result.out.joinToString("\n").trim(),
            result.err.joinToString("\n").trim()
        )
    }

    private fun execLocal(command: String): CommandResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = StringBuilder()
            val outReader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (outReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor(5, TimeUnit.SECONDS)
            CommandResult(process.exitValue(), output.toString().trim(), "")
        } catch (e: Exception) {
            CommandResult(-1, "", e.message ?: "Unknown local error")
        }
    }

    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val isSuccess: Boolean get() = exitCode == 0
    }
    /**
     * Executes a command and streams output line by line.
     * @param onOutput Callback for each line of stdout/stderr
     */
    fun execStreaming(command: String, onOutput: (String) -> Unit): CommandResult {
        return if (Shell.getShell().isRoot) {
            execRootStreaming(command, onOutput)
        } else {
            execLocalStreaming(command, onOutput)
        }
    }

    private fun execRootStreaming(command: String, onOutput: (String) -> Unit): CommandResult {
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        
        val stdoutCallback = object : java.util.ArrayList<String>() {
            override fun add(element: String): Boolean {
                stdout.append(element).append("\n")
                onOutput(element)
                return super.add(element)
            }
        }
        
        val stderrCallback = object : java.util.ArrayList<String>() {
            override fun add(element: String): Boolean {
                stderr.append(element).append("\n")
                onOutput("ERR: $element")
                return super.add(element)
            }
        }

        val result = Shell.cmd(command)
            .to(stdoutCallback)
            .to(stderrCallback)
            .exec()
            
        return CommandResult(result.code, stdout.toString().trim(), stderr.toString().trim())
    }

    private fun execLocalStreaming(command: String, onOutput: (String) -> Unit): CommandResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = StringBuilder()
            val error = StringBuilder()
            
            val outReader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            
            var line: String?
            while (outReader.readLine().also { line = it } != null) {
                line?.let {
                    output.append(it).append("\n")
                    onOutput(it)
                }
            }
            
            while (errReader.readLine().also { line = it } != null) {
                line?.let {
                    error.append(it).append("\n")
                    onOutput("ERR: $it")
                }
            }
            
            process.waitFor()
            CommandResult(process.exitValue(), output.toString().trim(), error.toString().trim())
        } catch (e: Exception) {
            val msg = e.message ?: "Unknown local error"
            onOutput("EXCEPTION: $msg")
            CommandResult(-1, "", msg)
        }
    }
}
