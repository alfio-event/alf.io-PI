package alfio.pi

import org.junit.Assert.assertEquals
import org.junit.Test

class ApplicationTest {
    @Test
    fun testCategoryKeyGeneration() {
        assertEquals("test-test", getCategoryKey("test Test"))
        assertEquals("one-234-five", getCategoryKey("One 2.3.4. Five"))
        assertEquals("1-abcd-h1330-test1-test2-bcd", getCategoryKey("1 ABCD h.13.30 - Test1 Test2 à bcd"))
        assertEquals("1-abcd-h1330-test1-test2-bcd", getCategoryKey("1 ABCD h.13.30 - T-est1 Test2 à bcd"))
    }
}