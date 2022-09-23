package com.igalia.wolvic.ui.views.library;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.databinding.HistoryFooterBinding;

public class HistoryFooter extends FrameLayout {

    private HistoryFooterBinding mBinding;

    public HistoryFooter(@NonNull Context context) {
        super(context);

        initialize(context, null, 0, 0);
    }

    public HistoryFooter(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        initialize(context, attrs, 0, 0);
    }

    public HistoryFooter(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        initialize(context, attrs, defStyleAttr, 0);
    }

    public HistoryFooter(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        initialize(context, attrs, defStyleAttr, defStyleRes);
    }

    private void initialize(@NonNull Context aContext, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.history_footer, this, true);
    }

    public void setFooterButtonClickListener(@NonNull OnClickListener listener) {
       mBinding.setResetClickListener(listener);
    }
    public void setFooterCancelButtonClickListener(@NonNull OnClickListener listener) {
       mBinding.setCancelClickListener(listener);
    }
}
