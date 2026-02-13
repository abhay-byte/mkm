package com.ivarna.mkm.shell

import com.topjohnwu.superuser.Shell
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Manages shell command execution with intelligent fallback.
 * Priority: Root (if available) → Local shell
 * 
 * Note: Shizuku is integrated for permission detection and status display.
 * Direct command execution via Shizuku requires UserService API (planned for future update).
 * For now, operations that require elevated access will use root.
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
     * Priority: Root → Local
     */
    fun getAvailableMethod(): AccessMethod {
        return if (Shell.getShell().isRoot) AccessMethod.ROOT else AccessMethod.LOCAL
    }

    /**
     * Check if elevated access is available (Root or Shizuku)
     * Note: Currently detects Shizuku but commands run via root
     */
    fun hasElevatedAccess(): Boolean {
        // Check if Shizuku is available and permitted
        val hasShizuku = ShizukuManager.hasPermission()
        
        // Check if root is available
        val hasRoot = Shell.getShell().isRoot
        
        // Return true if either is available
        return hasShizuku || hasRoot
    }
    
    /**
     * Check if Shizuku is available and has permission
     */
    fun hasShizuku(): Boolean {
        return ShizukuManager.hasPermission()
    }
    
    /**
     * Check if root access is available via libsu
     */
    fun hasRoot(): Boolean {
        return Shell.getShell().isRoot
    }

    /**
     * Execute command with automatic fallback
     * Shizuku → Root → Local shell
     */
    fun exec(command: String): CommandResult {
        // Try Shizuku first if available
        if (hasShizuku()) {
            val result = execShizuku(command)
            // If Shizuku succeeds or is truly unavailable, return the result
            // If it times out or fails, fall back to root
            if (result.isSuccess || result.exitCode != -1) {
                return result
            }
            // Shizuku failed, try root fallback
        }
        
        // Fall back to root or local
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
     * Execute via Shizuku (ADB shell with uid=2000)
     */
    private fun execShizuku(command: String): CommandResult {
        return try {
            // Use reflection to access private Shizuku.newProcess()
            val method = rikka.shizuku.Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            
            val process = method.invoke(
                null,  // static method
                arrayOf("sh", "-c", command),
                null,  // environment
                null   // working directory
            ) as Process
            
            // Close stdin immediately to prevent process from waiting for input
            process.outputStream.close()
            
            val output = StringBuilder()
            val error = StringBuilder()
            
            // Read streams in parallel threads to avoid deadlock
            val outThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        reader.lineSequence().forEach { line ->
                            output.append(line).append("\n")
                        }
                    }
                } catch (e: Exception) {
                    // Stream closed, normal for process completion
                }
            }
            
            val errThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                        reader.lineSequence().forEach { line ->
                            error.append(line).append("\n")
                        }
                    }
                } catch (e: Exception) {
                    // Stream closed, normal for process completion
                }
            }
            
            outThread.start()
            errThread.start()
            
            // Wait for process with timeout
            val finished = process.waitFor(10, TimeUnit.SECONDS)
            
            // Wait for threads to finish reading
            outThread.join(1000)
            errThread.join(1000)
            
            if (!finished) {
                process.destroy()
                return CommandResult(-1, output.toString().trim(), "Command timeout after 10 seconds")
            }
            
            val exitCode = process.exitValue()
            CommandResult(exitCode, output.toString().trim(), error.toString().trim())
        } catch (e: Exception) {
            CommandResult(-1, "", "Shizuku execution failed: ${e.message}")
        }
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
        // Try Shizuku first if available
        if (hasShizuku()) {
            val result = execShizukuStreaming(command, onOutput)
            // Fall back to root if Shizuku fails
            if (result.isSuccess || result.exitCode != -1) {
                return result
            }
            onOutput("Shizuku failed, falling back to root...")
        }
        
        // Fall back to root or local
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

    private fun execShizukuStreaming(command: String, onOutput: (String) -> Unit): CommandResult {
        return try {
            // Use reflection to access private Shizuku.newProcess()
            val method = rikka.shizuku.Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process
            
            // Close stdin immediately
            process.outputStream.close()
            
            val output = StringBuilder()
            val error = StringBuilder()
            
            // Read streams in parallel threads
            val outThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        reader.lineSequence().forEach { line ->
                            output.append(line).append("\n")
                            onOutput(line)
                        }
                    }
                } catch (e: Exception) {
                    // Stream closed
                }
            }
            
            val errThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                        reader.lineSequence().forEach { line ->
                            error.append(line).append("\n")
                            onOutput("ERR: $line")
                        }
                    }
                } catch (e: Exception) {
                    // Stream closed
                }
            }
            
            outThread.start()
            errThread.start()
            
            val finished = process.waitFor(10, TimeUnit.SECONDS)
            
            outThread.join(1000)
            errThread.join(1000)
            
            if (!finished) {
                process.destroy()
                val msg = "Command timeout after 10 seconds"
                onOutput("TIMEOUT: $msg")
                return CommandResult(-1, output.toString().trim(), msg)
            }
            
            val exitCode = process.exitValue()
            CommandResult(exitCode, output.toString().trim(), error.toString().trim())
        } catch (e: Exception) {
            val msg = "Shizuku streaming execution failed: ${e.message}"
            onOutput("EXCEPTION: $msg")
            CommandResult(-1, "", msg)
        }
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
