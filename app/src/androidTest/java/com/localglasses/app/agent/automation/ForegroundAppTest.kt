package com.localglasses.app.agent.automation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForegroundAppTest {

    @Test
    fun reportsAForegroundPackageOrNullWithoutThrowing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageName = ForegroundApp(context).currentPackage()
        assertNotNull(packageName)
    }
}
