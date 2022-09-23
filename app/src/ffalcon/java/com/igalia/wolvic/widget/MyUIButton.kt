package com.igalia.wolvic.widget

import android.content.Context
import android.util.AttributeSet
import com.igalia.wolvic.ui.views.UIButton

class MyUIButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : UIButton(context, attrs, defStyleAttr) {
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.4f
    }
}