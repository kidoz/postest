package su.kidoz.postest.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureTest {
    @Test fun `domain sources cannot depend on adapters presentation or database framework types`() {
        val root = File("src/main/kotlin/su/kidoz/postest/domain")
        assertTrue(root.isDirectory)
        val forbidden = Regex("su\\.kidoz\\.postest\\.(data|ui|viewmodel)\\.|io\\.ktor\\.|app\\.cash\\.sqldelight\\.")
        val violations =
            root
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { forbidden.containsMatchIn(it.readText()) }
                .map { it.relativeTo(root).path }
                .toList()
        assertTrue(violations.isEmpty(), "Domain dependencies point outside the boundary: $violations")
    }

    @Test fun `feature managers have no generic write access to application state`() {
        val root = File("src/main/kotlin/su/kidoz/postest/viewmodel/manager")
        assertTrue(root.isDirectory)
        val forbidden = Regex("StateUpdater|MutableStateFlow<AppState>")
        val violations =
            root
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { forbidden.containsMatchIn(it.readText()) }
                .map { it.name }
                .toList()
        assertTrue(violations.isEmpty(), "Managers must use typed workspace commands: $violations")
    }
}
