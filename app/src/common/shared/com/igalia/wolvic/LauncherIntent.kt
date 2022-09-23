package com.igalia.wolvic

import com.igalia.wolvic.browser.api.WSessionSettings

/**
 * @param url target URL
 * @param dof degree of freedom
 * @param iconsTemplate icons template on mobile
 * @param userAgentMode user agent mode [com.igalia.wolvic.browser.api.WSessionSettings]
 *
 */
data class LauncherIntent(
    val url: String = "",
    val dof: Int = DOF_0,
    val iconsTemplate: String = ICONS_TEMPLATE_NAVIGATION,
    val userAgentMode: Int = WSessionSettings.USER_AGENT_MODE_MOBILE
)

const val DOF_0 = 0
const val DOF_3 = 3

const val ICONS_TEMPLATE_DEFAULT = "default"
const val ICONS_TEMPLATE_NAVIGATION= "navigation"
const val ICONS_TEMPLATE_IMMERSIVE = "immersive"

const val INTENT_URI_DATA = "data"
