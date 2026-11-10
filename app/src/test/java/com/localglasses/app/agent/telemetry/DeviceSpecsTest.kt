package com.localglasses.app.agent.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceSpecsTest {

    @Test
    fun api31UsesSocModelWhenPresent() {
        assertEquals(
            "Dimensity 7300",
            DeviceSpecsFactory.systemOnChip(31, "Dimensity 7300", "mt6897"),
        )
    }

    @Test
    fun api31FallsBackToHardwareWhenSocModelIsBlank() {
        assertEquals(
            "mt6897",
            DeviceSpecsFactory.systemOnChip(31, "  ", "mt6897"),
        )
    }

    @Test
    fun pre31UsesHardwareOnly() {
        assertEquals(
            "mt6897",
            DeviceSpecsFactory.systemOnChip(30, "Dimensity 7300", "mt6897"),
        )
    }

    @Test
    fun missingValuesStayNull() {
        val specs = DeviceSpecsFactory.create(
            sdkInt = 30,
            model = "",
            socModel = null,
            hardware = null,
            abis = listOf("arm64-v8a", ""),
            androidVersion = null,
            memoryClassMb = null,
        )

        assertNull(specs.deviceModel)
        assertNull(specs.soc)
        assertNull(specs.androidVersion)
        assertNull(specs.memoryClassMb)
        assertEquals("arm64-v8a", specs.abis)
    }
}
