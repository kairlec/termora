package app.termora.windows.shell

import java.nio.file.Path

data class LocalShell(
    val displayName: String,
    val executablePath: Path,
    val arguments: List<String> = emptyList(),
    val homeDirectory: String? = null
)

/**
 * Detects all available local shells
 * Such as Windows PowerShell, Windows Command Line Prompt, WSL, PowerShell, etc.
 */
object LocalShellDetect {
    fun getSupportAllPath(): List<LocalShell> {
        return buildList {
            LegacyWindowsCommandLine.execPath?.let {
                add(LocalShell("Windows CommandLine Prompt", it))
            }
            LegacyWindowsPowerShell.execPath?.let {
                add(LocalShell("WindowsPowerShell", it))
            }
            PowerShell.collectPowerShellInstances().forEach {
                add(LocalShell(it.name(), it.executablePath))
            }
            // TODO start with arguments is not supported yet.
//            Wsl.enumerateDistributions().forEach {
//                add(LocalShell("WSL - ${it.distributionName}", it.wslExecutablePath, it.startArguments, it.defaultHomeDirectory))
//            }
        }
    }
}