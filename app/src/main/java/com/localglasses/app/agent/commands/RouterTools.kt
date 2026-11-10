package com.localglasses.app.agent.commands

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/**
 * Tools the model may select. With `automaticToolCalling = false` the runtime only
 * emits the call; the app validates it and builds the plan. The bodies are therefore
 * not the execution path.
 */
class RouterTools : ToolSet {

    @Tool("Call a contact by name or saved alias")
    fun callContact(
        @ToolParam("Contact name exactly as the user said it") contact: String,
    ): String = ""

    @Tool("Send an SMS to a contact")
    fun sendSms(
        @ToolParam("Contact name exactly as the user said it") contact: String,
        @ToolParam("Message text to send") message: String,
    ): String = ""

    @Tool("Send a WhatsApp message to a contact")
    fun sendWhatsapp(
        @ToolParam("Contact name exactly as the user said it") contact: String,
        @ToolParam("Message text to send") message: String,
    ): String = ""

    @Tool("Play a YouTube or YouTube Music search")
    fun playMedia(
        @ToolParam("Search text, for example an artist or song") query: String,
        @ToolParam("Either youtube or youtube_music") service: String,
    ): String = ""

    @Tool("Change a curated device setting")
    fun setDeviceSetting(
        @ToolParam("Setting id, for example screen_timeout_seconds") setting: String,
        @ToolParam("Integer value for the setting") value: Int,
    ): String = ""

    @Tool("Open a Settings page")
    fun openSettingsPage(
        @ToolParam("Page id, for example display, sound, wifi, bluetooth") page: String,
    ): String = ""

    @Tool("Plan UI steps when no registry action fits")
    fun planUiTask(
        @ToolParam("What the user wants to achieve") goal: String,
        @ToolParam("Target app package if the user named one") app: String?,
    ): String = ""
}

object RouterToolNames {
    const val CALL_CONTACT = "call_contact"
    const val SEND_SMS = "send_sms"
    const val SEND_WHATSAPP = "send_whatsapp"
    const val PLAY_MEDIA = "play_media"
    const val SET_DEVICE_SETTING = "set_device_setting"
    const val OPEN_SETTINGS_PAGE = "open_settings_page"
    const val PLAN_UI_TASK = "plan_ui_task"
}
