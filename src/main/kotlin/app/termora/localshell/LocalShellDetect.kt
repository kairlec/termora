package app.termora.localshell

import app.termora.localshell.windows.LegacyWindowsCommandLine
import app.termora.localshell.windows.LegacyWindowsPowerShell
import app.termora.localshell.windows.PowerShell
import app.termora.localshell.windows.Wsl
import com.formdev.flatlaf.util.SystemInfo
import com.sun.jna.LastErrorException
import java.nio.file.Path
import kotlin.io.path.pathString
import org.slf4j.LoggerFactory

data class LocalShell(
    val id: String,
    val displayName: String,
    val executablePath: Path,
    val arguments: List<String> = emptyList(),
    val homeDirectory: String? = null
) {
    override fun toString(): String {
        return buildString {
            append(displayName)
            append(" (")
            append(executablePath.pathString)
            arguments.forEach {
                append(" ")
                append(it)
            }
            append(")")
        }
    }
}

/**
 * Detects all available local shells
 * Such as Windows PowerShell, Windows Command Line Prompt, WSL, PowerShell, etc.
 */
object LocalShellDetect {
    private val localShellMaps: Map<String, LocalShell> by lazy {
        if (SystemInfo.isWindows) {
            detectWindowsLocalShells()
        } else {
            detectUnixLocalShells()
        }.associateBy { it.id }
    }

    private fun detectUnixLocalShells(): List<LocalShell> {
        return runCatching {
            val process = ProcessBuilder("cat", "/etc/shells").start()
            if (process.waitFor() != 0) {
                throw LastErrorException(process.exitValue())
            }
            process.inputStream.use { it.readAllBytes() }
                .toString(Charsets.UTF_8)
                .lines()
                .filter { e -> !e.trimStart().startsWith('#') }
                .filter { e -> e.isNotBlank() }
                .map { it.trim() }
        }.recover {
            logger.error("Failed to detect local shells: ${it.message}", it)
            val shell = System.getenv("SHELL")
            if (shell != null && shell.isNotBlank()) {
                listOf(shell)
            } else {
                emptyList()
            }
        }.map {
            it.ifEmpty { listOf("/bin/bash", "/bin/csh", "/bin/dash", "/bin/ksh", "/bin/sh", "/bin/tcsh", "/bin/zsh") }
                .map { shellPath -> LocalShell(shellPath, shellPath.substringAfterLast('/'), Path.of(shellPath)) }
        }.getOrElse { emptyList() }
    }

    private fun detectWindowsLocalShells(): List<LocalShell> {
        return buildList {
            LegacyWindowsPowerShell.execPath?.let {
                add(LocalShell("win-builtin:powershell", "Windows PowerShell", it))
            }
            LegacyWindowsCommandLine.execPath?.let {
                add(LocalShell("win-builtin:cmd", "Windows CommandLine Prompt", it))
            }
            runCatching {
                PowerShell.collectPowerShellInstances().forEach {
                    add(LocalShell(it.id(), it.name(), it.executablePath))
                }
            }.onFailure {
                logger.error("Failed to detect PowerShell instances: ${it.message}", it)
            }
            runCatching {
                Wsl.enumerateDistributions().forEach {
                    add(LocalShell(it.id(), "WSL - ${it.distributionName}", it.wslExecutablePath, it.startArguments))
                }
            }.onFailure {
                logger.error("Failed to detect WSL distributions: ${it.message}", it)
            }
        }
    }

    fun getLocalShellById(id: String): LocalShell? {
        return localShellMaps[id]
    }

    fun getSupportAllLocalShell(): Collection<LocalShell> {
        return localShellMaps.values
    }

    private val logger = LoggerFactory.getLogger(LocalShellDetect::class.java)
}