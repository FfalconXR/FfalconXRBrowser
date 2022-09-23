package com.igalia.wolvic.ui.widgets.settings;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;

import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.databinding.OptionsPermissionBinding;
import com.igalia.wolvic.ui.views.settings.SwitchSetting;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.utils.DeviceType;

import java.util.ArrayList;

/**
 * A SettingsView that displays a legal document like the Terms of Service or the Privacy Policy,.
 * The content itself is shared with LegalDocumentDialogWidget.
 */
public class PermissionView extends SettingsView {

    private OptionsPermissionBinding mBinding;
    private ArrayList<Pair<SwitchSetting, String>> mPermissionButtons;
    public PermissionView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        updateUI();
        ((Application)aContext.getApplicationContext()).registerActivityLifecycleCallbacks(mLifeCycleListener);
    }

    @Override
    protected void updateUI() {
        super.updateUI();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_permission, this, true);

        mBinding.headerLayout.setTitle(getContext().getString(R.string.security_options_permissions_title, getContext().getString(R.string.app_name)));
        mPermissionButtons = new ArrayList<>();
        mPermissionButtons.add(Pair.create(findViewById(R.id.cameraPermissionSwitch), Manifest.permission.CAMERA));
        mPermissionButtons.add(Pair.create(findViewById(R.id.microphonePermissionSwitch), Manifest.permission.RECORD_AUDIO));
        mPermissionButtons.add(Pair.create(findViewById(R.id.locationPermissionSwitch), Manifest.permission.ACCESS_FINE_LOCATION));
        mPermissionButtons.add(Pair.create(findViewById(R.id.storagePermissionSwitch), Manifest.permission.WRITE_EXTERNAL_STORAGE));

        if (DeviceType.isOculusBuild() || DeviceType.isWaveBuild() || DeviceType.isPicoVR()) {
            findViewById(R.id.cameraPermissionSwitch).setVisibility(View.GONE);
        }
        if (DeviceType.isOculusBuild()) {
            findViewById(R.id.locationPermissionSwitch).setVisibility(View.GONE);
        }

        for (Pair<SwitchSetting, String> button: mPermissionButtons) {
            button.first.setChecked(mWidgetManager.isPermissionGranted(button.second));
            button.first.setOnCheckedChangeListener((compoundButton, enabled, apply) ->
                    togglePermission(button.first, button.second));
        }

        mBinding.headerLayout.setBackClickListener(view -> {
            mDelegate.showView(SettingViewType.MAIN);
        });
    }

    @Override
    public void onShown() {
        super.onShown();
        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_NO_DIM_BRIGHTNESS);
    }

    @Override
    public void onHidden() {
        super.onHidden();
        mWidgetManager.popWorldBrightness(this);
    }

//    @Override
//    public Point getDimensions() {
//        return new Point(WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_width),
//                WidgetPlacement.dpDimension(getContext(), R.dimen.privacy_options_height));
//    }

    @Override
    public void releasePointerCapture() {
        super.releasePointerCapture();
        ((Application)getContext().getApplicationContext()).unregisterActivityLifecycleCallbacks(mLifeCycleListener);
    }

    @Override
    protected SettingViewType getType() {
        return SettingViewType.PERMISSION;
    }
    private void togglePermission(SwitchSetting aButton, String aPermission) {
        if (mWidgetManager.isPermissionGranted(aPermission)) {
            showAlert(aButton.getDescription(), getContext().getString(R.string.security_options_permissions_reject_message));
            aButton.setChecked(true);

        } else {
            mWidgetManager.requestPermission("", aPermission, new WSession.PermissionDelegate.Callback() {
                @Override
                public void grant() {
                    aButton.setChecked(true);
                }
                @Override
                public void reject() {

                }
            });
        }
    }


    private Application.ActivityLifecycleCallbacks mLifeCycleListener = new Application.ActivityLifecycleCallbacks() {

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

        }

        @Override
        public void onActivityStarted(Activity activity) {

        }

        @Override
        public void onActivityResumed(Activity activity) {
            // Refresh permission settings status after a permission has been requested
            for (Pair<SwitchSetting, String> button: mPermissionButtons) {
                button.first.setValue(mWidgetManager.isPermissionGranted(button.second), false);
            }
        }

        @Override
        public void onActivityPaused(Activity activity) {

        }

        @Override
        public void onActivityStopped(Activity activity) {

        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            activity.getApplication().unregisterActivityLifecycleCallbacks(mLifeCycleListener);
        }
    };
}