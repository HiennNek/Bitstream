package dev.hien.bitstream.core.designsystem

import dev.hien.bitstream.core.model.BitPerfectPath
import dev.hien.bitstream.core.model.Integrity
import dev.hien.bitstream.core.model.VolumeState
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusChipTest {
    @Test
    fun `hardware volume keeps bit perfect label`() {
        assertEquals("Bit-perfect", pathLabel(BitPerfectPath.BIT_PERFECT, VolumeState.Hardware(50)))
    }

    @Test
    fun `software volume demotes path`() {
        assertEquals("Software volume", pathLabel(BitPerfectPath.SOFTWARE_VOLUME, VolumeState.Software()))
    }

    @Test
    fun `missing md5 is unavailable not failed`() {
        assertEquals("Unavailable", integrityLabel(Integrity.UNAVAILABLE))
    }

    @Test
    fun `verified md5 with software volume shows decode verified`() {
        assertEquals("MD5 verified", integrityLabel(Integrity.VERIFIED, VolumeState.Hardware(80)))
        assertEquals("Decode verified", integrityLabel(Integrity.VERIFIED, VolumeState.Software()))
    }

    @Test
    fun `underrun label formats count`() {
        assertEquals("0 underruns", underrunLabel(0))
        assertEquals("1 underrun", underrunLabel(1))
        assertEquals("3 underruns", underrunLabel(3))
    }
}
