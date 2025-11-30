package su.kidoz.postest.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppVersionTest {
    @Test
    fun `compare returns zero for equal versions`() {
        assertEquals(0, AppVersion.compare("1.0.0", "1.0.0"))
        assertEquals(0, AppVersion.compare("2.1.3", "2.1.3"))
        assertEquals(0, AppVersion.compare("0.0.1", "0.0.1"))
    }

    @Test
    fun `compare returns positive when first version is greater`() {
        assertTrue(AppVersion.compare("2.0.0", "1.0.0") > 0)
        assertTrue(AppVersion.compare("1.1.0", "1.0.0") > 0)
        assertTrue(AppVersion.compare("1.0.1", "1.0.0") > 0)
        assertTrue(AppVersion.compare("1.10.0", "1.9.0") > 0)
    }

    @Test
    fun `compare returns negative when first version is smaller`() {
        assertTrue(AppVersion.compare("1.0.0", "2.0.0") < 0)
        assertTrue(AppVersion.compare("1.0.0", "1.1.0") < 0)
        assertTrue(AppVersion.compare("1.0.0", "1.0.1") < 0)
        assertTrue(AppVersion.compare("1.9.0", "1.10.0") < 0)
    }

    @Test
    fun `compare handles versions with v prefix`() {
        assertEquals(0, AppVersion.compare("v1.0.0", "1.0.0"))
        assertEquals(0, AppVersion.compare("1.0.0", "v1.0.0"))
        assertEquals(0, AppVersion.compare("v1.0.0", "v1.0.0"))
        assertTrue(AppVersion.compare("v2.0.0", "v1.0.0") > 0)
    }

    @Test
    fun `compare handles versions with different segment counts`() {
        assertEquals(0, AppVersion.compare("1.0", "1.0.0"))
        assertEquals(0, AppVersion.compare("1.0.0", "1.0"))
        assertTrue(AppVersion.compare("1.0.1", "1.0") > 0)
        assertTrue(AppVersion.compare("1.0", "1.0.1") < 0)
    }

    @Test
    fun `isNewerVersion returns true when new version is greater`() {
        assertTrue(AppVersion.isNewerVersion("1.0.0", "2.0.0"))
        assertTrue(AppVersion.isNewerVersion("1.0.0", "1.1.0"))
        assertTrue(AppVersion.isNewerVersion("1.0.0", "1.0.1"))
        assertTrue(AppVersion.isNewerVersion("1.0.0", "v1.0.1"))
    }

    @Test
    fun `isNewerVersion returns false when versions are equal`() {
        assertFalse(AppVersion.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(AppVersion.isNewerVersion("v1.0.0", "1.0.0"))
    }

    @Test
    fun `isNewerVersion returns false when new version is older`() {
        assertFalse(AppVersion.isNewerVersion("2.0.0", "1.0.0"))
        assertFalse(AppVersion.isNewerVersion("1.1.0", "1.0.0"))
        assertFalse(AppVersion.isNewerVersion("1.0.1", "1.0.0"))
    }

    @Test
    fun `CURRENT version is a valid semver string`() {
        val parts = AppVersion.CURRENT.split(".")
        assertTrue(parts.isNotEmpty())
        assertTrue(parts.all { it.toIntOrNull() != null })
    }
}
