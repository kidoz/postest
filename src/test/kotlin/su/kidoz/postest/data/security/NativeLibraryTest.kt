package su.kidoz.postest.data.security

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeLibraryTest {
    @Test
    fun `mac native bridge creates and reads a CFData without accessing the keychain`() {
        assumeTrue(System.getProperty("os.name").contains("Mac"))
        Arena.ofConfined().use { arena ->
            val core = NativeLibrary("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", arena)
            val bytes = arena.allocateFrom(ValueLayout.JAVA_BYTE, 1, 2, 3)
            val address = ValueLayout.ADDRESS
            val data =
                core.call(
                    "CFDataCreate",
                    address,
                    listOf(address, address, ValueLayout.JAVA_LONG),
                    java.lang.foreign.MemorySegment.NULL,
                    bytes,
                    3L,
                ) as java.lang.foreign.MemorySegment
            try {
                assertEquals(3L, core.call("CFDataGetLength", ValueLayout.JAVA_LONG, listOf(address), data))
                // Global CF object pointers must be sized before reading; no credential functions are called.
                val boolean = core.symbol("kCFBooleanTrue").reinterpret(address.byteSize()).get(address, 0)
                assertEquals(1.toByte(), core.call("CFBooleanGetValue", ValueLayout.JAVA_BYTE, listOf(address), boolean))
            } finally {
                core.call("CFRelease", null, listOf(address), data)
            }
        }
    }
}
