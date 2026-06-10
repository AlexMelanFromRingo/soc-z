package io.melan.socz

import io.melan.socz.collectors.GpuCollector
import io.melan.socz.collectors.MemoryCollector
import io.melan.socz.collectors.NetworkCollector
import io.melan.socz.collectors.SocCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsersTest {

    @Test
    fun adrenoModelParsed() {
        assertEquals(740, GpuCollector.parseAdreno("Adreno (TM) 740"))
        assertEquals(642, GpuCollector.parseAdreno("Adreno (TM) 642L"))
        assertNull(GpuCollector.parseAdreno("Mali-G715-Immortalis MC11"))
        assertNull(GpuCollector.parseAdreno(""))
    }

    @Test
    fun vulkanVersionDecoded() {
        assertEquals("1.3.0", GpuCollector.decodeVulkanVersion((1 shl 22) or (3 shl 12)))
        assertEquals("1.1.128", GpuCollector.decodeVulkanVersion((1 shl 22) or (1 shl 12) or 128))
        assertEquals("0.0.0", GpuCollector.decodeVulkanVersion(0))
    }

    @Test
    fun meminfoParsed() {
        val sample = """
            MemTotal:       11528332 kB
            MemFree:          318096 kB
            MemAvailable:    4280144 kB
            HugePages_Total:       0
            garbage line without separator
            SwapTotal:       4194300 kB
        """.trimIndent()
        val m = MemoryCollector.parseMeminfo(sample)
        assertEquals(11528332L, m["MemTotal"])
        assertEquals(4280144L, m["MemAvailable"])
        assertEquals(4194300L, m["SwapTotal"])
        assertEquals(0L, m["HugePages_Total"])
        assertEquals(5, m.size)
    }

    @Test
    fun wifiStandardNamed() {
        assertEquals("Wi-Fi 4 (802.11n)", NetworkCollector.wifiStandardName(4))
        assertEquals("Wi-Fi 5 (802.11ac)", NetworkCollector.wifiStandardName(5))
        assertEquals("Wi-Fi 6 (802.11ax)", NetworkCollector.wifiStandardName(6))
        assertEquals("Wi-Fi 7 (802.11be)", NetworkCollector.wifiStandardName(8))
        assertNull(NetworkCollector.wifiStandardName(0)) // WIFI_STANDARD_UNKNOWN
    }

    @Test
    fun cpuinfoFieldExtractedFromFirstBlock() {
        val cpuinfo = """
            processor	: 0
            Features	: fp asimd evtstrm aes
            CPU implementer	: 0x41
            CPU architecture: 8

            processor	: 1
            CPU implementer	: 0x51
        """.trimIndent()
        assertEquals("0x41", SocCollector.cpuinfoField(cpuinfo, "CPU implementer"))
        assertEquals("fp asimd evtstrm aes", SocCollector.cpuinfoField(cpuinfo, "Features"))
        assertEquals("8", SocCollector.cpuinfoField(cpuinfo, "CPU architecture"))
        assertEquals("", SocCollector.cpuinfoField(cpuinfo, "Hardware"))
        assertEquals("", SocCollector.cpuinfoField("", "Features"))
    }
}
