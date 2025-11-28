package su.kidoz.postest.security

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Paths

private val logger = KotlinLogging.logger {}

object SecretStoreFactory {
    fun create(): SecretStore {
        val home = System.getProperty("user.home")
        val baseDir = Paths.get(home, ".postest", "secrets")
        val keyPath = Paths.get(home, ".postest", "keys", "master.key")

        // Placeholder for future platform-specific stores; currently file-backed AES-GCM.
        val store: SecretStore = FileSecretStore(baseDir, keyPath)
        logger.info { "Using secret store backend: ${store.name}" }
        return store
    }
}
