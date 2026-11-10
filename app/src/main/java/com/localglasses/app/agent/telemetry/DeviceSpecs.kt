package com.localglasses.app.agent.telemetry

import android.app.ActivityManager
import android.content.Context
import android.os.Build

data class DeviceSpecs(
    val deviceModel: String?,
    val soc: String?,
    val abis: String?,
    val androidVersion: String?,
    val memoryClassMb: Int?,
)

/** Pure selection logic so the API-31 and pre-31 branches are unit testable. */
object DeviceSpecsFactory {

    fun systemOnChip(sdkInt: Int, socModel: String?, hardware: String?): String? =
        if (sdkInt >= Build.VERSION_CODES.S) {
            socModel?.takeIf { it.isNotBlank() } ?: hardware?.takeIf { it.isNotBlank() }
        } else {
            hardware?.takeIf { it.isNotBlank() }
        }

    fun create(
        sdkInt: Int,
        model: String?,
        socModel: String?,
        hardware: String?,
        abis: List<String>,
        androidVersion: String?,
        memoryClassMb: Int?,
    ): DeviceSpecs = DeviceSpecs(
        deviceModel = model?.takeIf { it.isNotBlank() },
        soc = systemOnChip(sdkInt, socModel, hardware),
        abis = abis.filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() },
        androidVersion = androidVersion?.takeIf { it.isNotBlank() },
        memoryClassMb = memoryClassMb,
    )
}

class AndroidDeviceSpecs(context: Context) {

    private val appContext = context.applicationContext

    fun read(): DeviceSpecs {
        val memoryClassMb = runCatching {
            appContext.getSystemService(ActivityManager::class.java).memoryClass
        }.getOrNull()
        return DeviceSpecsFactory.create(
            sdkInt = Build.VERSION.SDK_INT,
            model = Build.MODEL,
            socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else null,
            hardware = Build.HARDWARE,
            abis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            androidVersion = Build.VERSION.RELEASE,
            memoryClassMb = memoryClassMb,
        )
    }
}
