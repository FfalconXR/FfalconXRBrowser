/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.text.Html;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.audio.AudioEngine;
import com.igalia.wolvic.browser.Accounts;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.api.WRuntime;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.NewSettingsBinding;
import com.igalia.wolvic.databinding.SettingsBinding;
import com.igalia.wolvic.db.SitePermission;
import com.igalia.wolvic.search.SearchEngineWrapper;
import com.igalia.wolvic.telemetry.TelemetryService;
import com.igalia.wolvic.ui.viewmodel.SettingsViewModel;
import com.igalia.wolvic.ui.views.settings.SwitchSetting;
import com.igalia.wolvic.ui.widgets.UIWidget;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.ui.widgets.WindowWidget;
import com.igalia.wolvic.ui.widgets.dialogs.RestartDialogWidget;
import com.igalia.wolvic.ui.widgets.dialogs.UIDialog;
import com.igalia.wolvic.utils.RemoteProperties;
import com.igalia.wolvic.utils.StringUtils;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import mozilla.components.Build;
import mozilla.components.concept.storage.Login;
import mozilla.components.concept.sync.AccountObserver;
import mozilla.components.concept.sync.AuthFlowError;
import mozilla.components.concept.sync.AuthType;
import mozilla.components.concept.sync.OAuthAccount;
import mozilla.components.concept.sync.Profile;

public class SettingsWidget extends UIWidget implements SettingsView.Delegate {

    private AudioEngine mAudio;
    private SettingsView mCurrentView;
    private int mViewMarginH;
    private int mViewMarginV;
    private RestartDialogWidget mRestartDialog;
    private Accounts mAccounts;
    private Executor mUIThreadExecutor;
    private SettingsView.SettingViewType mOpenDialog;
    private SettingsViewModel mSettingsViewModel;
    private NewSettingsBinding mBinding;


    public SettingsWidget(Context aContext) {
        super(aContext);
        initialize();
    }

    public SettingsWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize();
    }

    public SettingsWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initialize() {
        mSettingsViewModel = new ViewModelProvider(
                (VRBrowserActivity)getContext(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication()))
                .get(SettingsViewModel.class);

        updateUI();

        mOpenDialog = SettingsView.SettingViewType.MAIN;

        mUIThreadExecutor = ((VRBrowserApplication)getContext().getApplicationContext()).getExecutors().mainThread();

        mAudio = AudioEngine.fromContext(getContext());
        mViewMarginH = mWidgetPlacement.width - WidgetPlacement.dpDimension(getContext(), R.dimen.options_width);
        mViewMarginH = WidgetPlacement.convertDpToPixel(getContext(), mViewMarginH);
        mViewMarginV = mWidgetPlacement.height - WidgetPlacement.dpDimension(getContext(), R.dimen.options_height);
        mViewMarginV = WidgetPlacement.convertDpToPixel(getContext(), mViewMarginV);
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.new_settings, this, true);
        mBinding.setSettingsmodel(mSettingsViewModel);
        String version = BuildConfig.VERSION_NAME;
        String appName = getContext().getString(R.string.app_name);
        String versionName = appName + version;
        mBinding.textVersion.setText(getContext().getString(R.string.settings_version) + versionName);

        mBinding.searchNewEngineButton.setOnClickListener(v -> {
            showView(SettingsView.SettingViewType.SEARCH_ENGINE);
        });
        String searchEngineName = SearchEngineWrapper.get(getContext()).getCurrentSearchEngine().getName();
        mBinding.searchEngineDescription.setText(searchEngineName);

        mBinding.layoutEnvironments.setOnClickListener(v -> {
            showView(SettingsView.SettingViewType.ENVIRONMENT);
        });
        mBinding.layoutCleanCache.setOnClickListener(v -> {
            SessionStore.get().clearCache(
                    WRuntime.ClearFlags.SITE_DATA |
                            WRuntime.ClearFlags.COOKIES |
                            WRuntime.ClearFlags.SITE_SETTINGS);
            SessionStore.get().clearCache(WRuntime.ClearFlags.ALL_CACHES);
        });

        mBinding.textPermissions.setText(getContext().getString(R.string.security_options_permissions_title, getContext().getString(R.string.app_name)));

        mBinding.layoutPrivacyPolicy.setOnClickListener(v -> {
            showView(SettingsView.SettingViewType.PRIVACY_POLICY);
        });
        mBinding.layoutPermission.setOnClickListener(v -> {
            showView(SettingsView.SettingViewType.PERMISSION);
        });
        mBinding.layoutTermsService.setOnClickListener(v -> {
            showView(SettingsView.SettingViewType.TERMS_OF_SERVICE);
        });

        mBinding.layoutAdvanced.setOnClickListener(v -> {
            showView(SettingsView.SettingViewType.POPUP_EXCEPTIONS);
        });
        mBinding.popUpsBlockingSwitch.setOnCheckedChangeListener(mPopUpsBlockingListener);
        setPopUpsBlocking(SettingsStore.getInstance(getContext()).isPopUpsBlockingEnabled(), false);

        mCurrentView = null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        showView(mOpenDialog);
    }

    @Override
    public void releaseWidget() {

        super.releaseWidget();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        int windowWidth = SettingsStore.getInstance(getContext()).getWindowWidth();
        aPlacement.width = windowWidth + mBorderWidth * 2;
        aPlacement.height = SettingsStore.getInstance(getContext()).getWindowHeight() + mBorderWidth * 2;
        aPlacement.worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width) *
                (float)windowWidth / (float)SettingsStore.WINDOW_WIDTH_DEFAULT;
        aPlacement.density = 1.0f;
        aPlacement.visible = true;
        aPlacement.cylinder = true;
        aPlacement.textureScale = 1.0f;
    }






    @Override
    public void showView(SettingsView.SettingViewType aType) {
        showView(aType, null);
    }

    @Override
    public void showView(SettingsView.SettingViewType aType, @Nullable Object extras) {
        switch (aType) {
            case MAIN:
                showView((SettingsView) null);
                break;
            case PERMISSION:
                showView(new PermissionView(getContext(), mWidgetManager));
                break;
            case POPUP_EXCEPTIONS:
                showView(new SitePermissionsOptionsView(getContext(), mWidgetManager, SitePermission.SITE_PERMISSION_POPUP));
                break;
            case ENVIRONMENT:
                showView(new EnvironmentOptionsView(getContext(), mWidgetManager));
                break;
            case SEARCH_ENGINE:
                showView(new SearchEngineView(getContext(), mWidgetManager));
                break;
            case TERMS_OF_SERVICE:
                showView(new LegalDocumentView(getContext(), mWidgetManager, LegalDocumentView.LegalDocument.TERMS_OF_SERVICE));
                break;
            case PRIVACY_POLICY:
                showView(new LegalDocumentView(getContext(), mWidgetManager, LegalDocumentView.LegalDocument.PRIVACY_POLICY));
                break;
        }
    }

    private void showView(SettingsView aView) {
        if (mCurrentView != null) {
            mCurrentView.onHidden();
            this.removeView(mCurrentView);
        }
        mCurrentView = aView;
        if (mCurrentView != null) {
            mOpenDialog = aView.getType();
            Point viewDimensions = mCurrentView.getDimensions();
            mViewMarginH = mWidgetPlacement.width - viewDimensions.x;
            mViewMarginH = WidgetPlacement.convertDpToPixel(getContext(), mViewMarginH);
            mViewMarginV = mWidgetPlacement.height - viewDimensions.y;
            mViewMarginV = WidgetPlacement.convertDpToPixel(getContext(), mViewMarginV);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            params.leftMargin = params.rightMargin = mViewMarginH / 2;
            params.topMargin = params.bottomMargin = mViewMarginV / 2;
            mCurrentView.onShown();
            this.addView(mCurrentView, params);
            mCurrentView.setDelegate(this);
            mBinding.optionsLayout.setVisibility(View.GONE);

        } else {
            updateUI();
            mBinding.optionsLayout.setVisibility(View.VISIBLE);
//            updateCurrentAccountState();
        }
    }

    private SwitchSetting.OnCheckedChangeListener mPopUpsBlockingListener = (compoundButton, value, doApply) -> {
        setPopUpsBlocking(value, doApply);
    };


    private void setPopUpsBlocking(boolean value, boolean doApply) {
        mBinding.popUpsBlockingSwitch.setOnCheckedChangeListener(null);
        mBinding.popUpsBlockingSwitch.setValue(value, false);
        mBinding.popUpsBlockingSwitch.setOnCheckedChangeListener(mPopUpsBlockingListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setPopUpsBlockingEnabled(value);
        }
    }


    public void show(int aShowFlags, @NonNull SettingsView.SettingViewType settingDialog) {
//        if (!isVisible()) {
//            show(aShowFlags);
//        }

        showView(settingDialog);
    }

//    @Override
//    public void show(@ShowFlags int aShowFlags) {
//        super.show(aShowFlags);
//
//        updateCurrentAccountState();
//    }

    // SettingsView.Delegate
    @Override
    public void onDismiss() {
        if (mCurrentView != null) {
            if (!mCurrentView.isEditing()) {
                if (isLanguagesSubView(mCurrentView)) {
                    showView(SettingsView.SettingViewType.LANGUAGE);

                } else if (isPrivacySubView(mCurrentView)) {
                    showView(SettingsView.SettingViewType.PRIVACY);

                } else if (isLoginsSubview(mCurrentView)) {
                    showView(SettingsView.SettingViewType.LOGINS_AND_PASSWORDS);

                } else if (isSavedLoginsSubview(mCurrentView)) {
                    showView(SettingsView.SettingViewType.SAVED_LOGINS);

                } else {
                    showView(SettingsView.SettingViewType.MAIN);
                }
            }
//        } else {
//            super.onDismiss();
        }
    }

    @Override
    public void exitWholeSettings() {
        showView(SettingsView.SettingViewType.MAIN);
        hide(UIWidget.REMOVE_WIDGET);
    }

    @Override
    public void showRestartDialog() {
        if (mRestartDialog == null) {
            mRestartDialog = new RestartDialogWidget(getContext());
        }

        mRestartDialog.show(REQUEST_FOCUS);
    }

    @Override
    public void showAlert(String aTitle, String aMessage) {
        mWidgetManager.getFocusedWindow().showAlert(aTitle, aMessage, null);
    }

    private boolean isLanguagesSubView(View view) {
        return view instanceof DisplayLanguageOptionsView ||
                view instanceof ContentLanguageOptionsView ||
                view instanceof VoiceSearchLanguageOptionsView;
    }

    private boolean isPrivacySubView(View view) {
        return (view instanceof SitePermissionsOptionsView &&
                ((SitePermissionsOptionsView)view).getType() != SettingsView.SettingViewType.LOGIN_EXCEPTIONS) ||
                view instanceof LoginAndPasswordsOptionsView;
    }

    private boolean isLoginsSubview(View view) {
        return (view instanceof SitePermissionsOptionsView &&
                ((SitePermissionsOptionsView)view).getType() == SettingsView.SettingViewType.LOGIN_EXCEPTIONS) ||
                view instanceof SavedLoginsOptionsView;
    }

    private boolean isSavedLoginsSubview(View view) {
        return view instanceof LoginEditOptionsView;
    }

}
