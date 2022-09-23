package com.igalia.wolvic.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;

import com.igalia.wolvic.ui.widgets.KeyboardWidget;

@SuppressLint("AppCompatCustomView")
public class BridgeEditText extends EditText {

    public BridgeEditText(Context context) {
        super(context);
    }

    public BridgeEditText(Context context, AttributeSet as) {
        super(context, as);
    }

    public BridgeEditText(Context context, AttributeSet as, int defStyle) {
        super(context, as, defStyle);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        //Kaidi,由于最终是由BridgeEditText来调用输入法，所以需要修改EditorInfo
        //来修改输入法显示的按钮。这里显示GO的按钮
        outAttrs.imeOptions |= EditorInfo.IME_ACTION_GO;
        if (KeyboardWidget.mInputConnection == null)
            return super.onCreateInputConnection(outAttrs);
        else
            return KeyboardWidget.mInputConnection;
    }
}

