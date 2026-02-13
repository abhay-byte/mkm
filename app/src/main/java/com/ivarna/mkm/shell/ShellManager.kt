package com.ivarna.mkm.shell

import com.topjohnwu.superuser.Shell
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Manages shell command execution with intelligent fallback.
 * Uses libsu for root access, local shell for non-root operations.
 * 
 * Note: Shizuku integration is for permission detection only.
 * Shizuku ADB mode cannot run su commands, so we rely on libsu for root access.
 */
object ShellManager {

    /**
     * Access method enum for identifying the current execution mode
     */
    enum class AccessMethod {
        ROOT,       // Root via libsu
        LOCAL       // Non-root local shell
    }

    /**
     * Get the currently available access method
     */
    fun getAvailableMethod(): AccessMethod {
        return if (Shell.getShell().isRoot) AccessMethod.ROOT else AccessMethod.LOCAL
    }

    /**
     * Check if elevated access is available (Root)
     */
    fun hasElevatedAccess(): Boolean {
        return Shell.getShell().isRoot
    }

    /**
     * Execute command with automatic fallback
     * Root → Local shell
     */
    fun exec(command: String): CommandResult {
        return when (getAvailableMethod()) {
            AccessMethod.ROOT -> execRoot(command)
            AccessMethod.LOCAL -> execLocal(command)
        }
    }

    /**
     * Execute via root (libsu)
     */
    private fun execRoot(command: String): CommandResult {
        val result = Shell.cmd(command).exec()
        return CommandResult(
            result.code,
            result.out.joinToString("\n").trim(),
            result.err.joinToString("\n").trim()
        )
    }

    /**
     * Execute via local shell (non-root)
     */
    private fun execLocal(command: String): CommandResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
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
            
            process.waitFor(10, TimeUnit.SECONDS)
            CommandResult(process.exitValue(), output.toString().trim(), error.toString().trim())
        } catch (e: Exception) {
            CommandResult(-1, "", e.message ?: "Unknown local error")
        }
    }

    /**
     * Command execution result
     */
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
        return when (getAvailableMethod()) {
            AccessMethod.ROOT -> execRootStreaming(command, onOutput)
            AccessMethod.LOCAL -> execLocalStreaming(command, onOutput)
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
