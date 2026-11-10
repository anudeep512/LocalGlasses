package com.localglasses.app.agent.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityEvent

class AgentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: AgentAccessibilityService? = null
            private set

        fun isConnected(): Boolean = instance != null

        fun isEnabled(context: Context): Boolean {
            if (instance != null) return true
            val manager = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
            return manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
            ).any {
                it.resolveInfo?.serviceInfo?.packageName == context.packageName
            }
        }
    }
}
