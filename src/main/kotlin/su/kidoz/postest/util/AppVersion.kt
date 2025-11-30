package su.kidoz.postest.util

/**
 * Application version information and comparison utilities.
 */
object AppVersion {
    const val CURRENT = "1.0.0"
    const val GITHUB_OWNER = "kidoz"
    const val GITHUB_REPO = "postest"

    /**
     * Compares two semantic version strings.
     * Returns:
     *  - negative if v1 < v2
     *  - zero if v1 == v2
     *  - positive if v1 > v2
     */
    fun compare(
        v1: String,
        v2: String,
    ): Int {
        val parts1 = v1.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLength) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }

    /**
     * Checks if newVersion is newer than currentVersion.
     */
    fun isNewerVersion(
        currentVersion: String,
        newVersion: String,
    ): Boolean = compare(newVersion, currentVersion) > 0
}
