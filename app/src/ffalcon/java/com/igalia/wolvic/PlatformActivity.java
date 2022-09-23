/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import android.app.Activity;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;

import androidx.annotation.Keep;
import androidx.lifecycle.ViewModelProvider;

import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.databinding.ActivityFfalconNewBinding;
import com.igalia.wolvic.ui.viewmodel.WindowViewModel;
import com.igalia.wolvic.ui.widgets.WindowWidget;
import com.igalia.wolvic.util.DataReportHelper;
import com.igalia.wolvic.util.DisplayUtil;
import com.igalia.wolvic.util.ProcessSelfKillTool;
import com.igalia.wolvic.util.SoftKeyBoardListener;
import com.igalia.wolvic.utils.SystemUtils;
import com.igalia.wolvic.widget.BridgeEditText;
import com.tcl.xr.api.AirApi;

import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PlatformActivity extends Activity implements IActivityCallback {
    static String LOGTAG = SystemUtils.createLogtag(PlatformActivity.class);
    static final float ROTATION = 0.098174770424681f;

    @SuppressWarnings("unused")
    public static boolean filterPermission(final String aPermission) {
        return false;
    }

    public static boolean isNotSpecialKey(KeyEvent event) {
        return true;
    }

    private final ArrayList<Runnable> mPendingEvents = new ArrayList<>();
    private boolean mSurfaceCreated = false;
    private boolean mIs0DofHeading = false;
    private boolean mIsSplashFinished = false;

    private WindowViewModel mViewModel;
    private ActivityFfalconNewBinding mBinding;

    final Object mRenderLock = new Object();

    private final Runnable activityDestroyedRunnable = () -> {
        synchronized (mRenderLock) {
            activityDestroyed();
            mRenderLock.notifyAll();
        }
    };

    private final Runnable activityPausedRunnable = () -> {
        synchronized (mRenderLock) {
            activityPaused();
            mRenderLock.notifyAll();
        }
    };

    private final Runnable activityResumedRunnable = this::activityResumed;

    /********************
     * Kaidi, Modify Begin
     *******************/
    private DisplayManager mDisplayManager;
    private Display targetDisplay;
    private MyPresentation mPresentation;

    private GLSurfaceView.Renderer mRenderer;
    private Handler mHandler;
    private AirApi airApi;
    public static PlatformActivity instance;
    private BridgeEditText bridgeEditText;
    private boolean displayAdded = false;

    public static void showInput() {
        Log.e("Kaidi", "showInput");
        if (instance == null)
            return;
        instance.bridgeEditText.setText("");
        InputMethodManager imm = (InputMethodManager) instance.getSystemService(Context.INPUT_METHOD_SERVICE);
        instance.bridgeEditText.requestFocus();
        //FIXME 输入法UI概率性弹不出来，不知道该如何解决
        imm.showSoftInput(instance.bridgeEditText, InputMethodManager.SHOW_FORCED);
    }

    public static void hideInput() {
        Log.e("Kaidi", "hideInput");
        if (instance == null)
            return;
        InputMethodManager imm = (InputMethodManager) instance.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(instance.bridgeEditText.getWindowToken(), 0);
    }

    public GLSurfaceView.Renderer getRenderer() {
        return mRenderer;
    }

    private DisplayManager.DisplayListener mDisplayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
            displayAdded = true;
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            displayAdded = false;
            if (targetDisplay == null)
                return;
            //If Glasses display is removed, just stop this Browser App
            if (targetDisplay.getDisplayId() == displayId) {
                finish();
            }
        }

        @Override
        public void onDisplayChanged(int displayId) {
            displayAdded = true;
        }
    };

    private void initDisplayListener() {
        mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);
    }

    private void releaseDisplayListener() {
        mDisplayManager.unregisterDisplayListener(mDisplayListener);
    }

    public void startPresentation() {
        mPresentation = new MyPresentation(this, targetDisplay);
        mPresentation.show();
    }

    public void releasePresentation() {
        if (mPresentation != null) {
            mPresentation.dismiss();
            mPresentation = null;
        }
    }

    private void initArcheryDevice() {
        airApi = new AirApi();
        airApi.init(this);
        airApi.openSensor();
        airApi.openMobileSensor();
    }

    private void releaseArcheryDevice() {
        if (airApi != null) {
//            airApi.closeSensor();
            //Kaidi,Client AAR切换2D不会生效；但是会通知Launcher，恢复界面以及音乐
            //不需要关闭眼镜的传感器
            airApi.switchTo2DMode();
            airApi.closeMobileSensor();
            airApi = null;
        }
    }

    private void resetSensor() {
        if (airApi != null) {
            airApi.resetYawForOpenGLCamera();
            airApi.resetYawForMobile();
        }
    }

    /********************
     * Kaidi, Modify End
     *******************/

    @Override
    protected void onStop() {
        super.onStop();
        //PlatformActivity到后台以后，隐藏眼睛上浏览器
        //浏览器到前台后，再恢复
        if (mPresentation != null)
            mPresentation.hide();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mPresentation != null)
            mPresentation.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(LOGTAG, "PlatformActivity onCreate");
        super.onCreate(savedInstanceState);
        this.mHandler = new Handler();
        this.mDisplayManager = (DisplayManager) this.getSystemService(Context.DISPLAY_SERVICE);

        targetDisplay = DisplayUtil.checkTCLGlasses(this);
        if (targetDisplay == null) {
            finish();
            return;
        }
        mBinding = ActivityFfalconNewBinding.inflate(LayoutInflater.from(this));
        mBinding.setLifecycleOwner((VRBrowserActivity) this);
        setContentView(mBinding.getRoot());
        mBinding.backButton.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            if (getSession().canGoBack()) {
                getSession().goBack();
            }
        });

        mBinding.forwardButton.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            getSession().goForward();
        });

        mBinding.reloadButton.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            if (mViewModel.getIsLoading().getValue().get()) {
                getSession().stop();
            } else {
                int flags = SettingsStore.getInstance(this).isBypassCacheOnReloadEnabled() ? WSession.LOAD_FLAGS_BYPASS_CACHE : WSession.LOAD_FLAGS_NONE;
                getSession().reload(flags);
            }
        });

        mBinding.homeButton.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            String originalUrl = ((VRBrowserActivity) this).getOriginalUrlFromLauncher();
            if (originalUrl.isEmpty()) {
                originalUrl = getSession().getHomeUri();
            }
            getSession().loadUri(originalUrl);
        });
        mBinding.resetButton.setOnClickListener(v -> resetSensor());
        mRenderer = new GLSurfaceView.Renderer() {
            @Override
            public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                Log.d(LOGTAG, "In onSurfaceCreated");
                activityCreated(getAssets());
                mSurfaceCreated = true;
                notifyPendingEvents();
            }

            @Override
            public void onSurfaceChanged(GL10 gl, int width, int height) {
                Log.d(LOGTAG, "In onSurfaceChanged");
                //Kaidi Side By Side，只传入一半宽度
                updateViewport(width / 2, height);
            }

            @Override
            public void onDrawFrame(GL10 gl) {
                float[] head = {0, 0, 0, 0};
                float[] mobile = {0, 0, 0, 0};

                if (airApi != null && mIsSplashFinished) {
                    if (!mIs0DofHeading) {
                        head = airApi.getPosQuaternion();
                    }
                    mobile = airApi.getMobilePosQuaternion();
                }

                setDeviceQuaternion(head[0], head[1], head[2], head[3]);
                setControllerQuaternion(mobile[0], mobile[1], mobile[2], mobile[3]);

                drawGL();
            }
        };
        setupUI();

        //Kaidi
        this.initArcheryDevice();
        this.startPresentation();
        this.initDisplayListener();
        SoftKeyBoardListener.setListener(this, new SoftKeyBoardListener.OnSoftKeyBoardChangeListener() {
            @Override
            public void keyBoardShow(int height) {
            }

            @Override
            public void keyBoardHide(int height) {
                VRBrowserActivity activity = (VRBrowserActivity) PlatformActivity.this;
                activity.hideKeyboardWidget();
            }
        });

        bridgeEditText = findViewById(R.id.bridge_edit_text);
        instance = this;
    }

    private Session getSession() {
        WindowWidget windowWidget = ((VRBrowserActivity) this).getFocusedWindow();
        return windowWidget.getSession();
    }

    @Override
    public boolean onTouchEvent(MotionEvent aEvent) {
        if (aEvent.getActionIndex() != 0) {
            Log.e(LOGTAG, "aEvent.getActionIndex()=" + aEvent.getActionIndex());
            return false;
        }

        int action = aEvent.getAction();
        boolean down;
        if (action == MotionEvent.ACTION_DOWN) {
            down = true;
        } else if (action == MotionEvent.ACTION_UP) {
            down = false;
        } else if (action == MotionEvent.ACTION_MOVE) {
            down = true;
        } else {
            return false;
        }

        final boolean isDown = down;

        final float xx = aEvent.getX(0);
        final float yy = aEvent.getY(0);
        queueRunnable(() -> touchEvent(isDown, xx, yy));
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent aEvent) {
        if (aEvent.getActionIndex() != 0) {
            Log.e(LOGTAG, "aEvent.getActionIndex()=" + aEvent.getActionIndex());
            return false;
        }

        if (aEvent.getAction() != MotionEvent.ACTION_HOVER_MOVE) {
            return false;
        }

        final float xx = aEvent.getX(0);
        final float yy = aEvent.getY(0);
        queueRunnable(() -> touchEvent(false, xx, yy));
        return true;
    }

    @Override
    protected void onPause() {
        Log.d(LOGTAG, "PlatformActivity onPause");
        if (mPresentation != null && displayAdded) {
            synchronized (mRenderLock) {
                queueRunnable(activityPausedRunnable);
                try {
                    mRenderLock.wait();
                } catch (InterruptedException e) {
                    Log.e(LOGTAG, "activityPausedRunnable interrupted: " + e.toString());
                }
            }
        }
        super.onPause();
        //暂停GLView，以避免退出Browser时，事件仍在发送，导致眼睛上闪动的问题
        if (mPresentation != null)
            mPresentation.pauseGLView();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mViewModel == null) {
            mViewModel = new ViewModelProvider(
                    (VRBrowserActivity) this,
                    ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication()))
                    .get(String.valueOf(((VRBrowserActivity) this).getFocusedWindow().hashCode()), WindowViewModel.class);
        }
        mBinding.setViewmodel(mViewModel);
    }

    @Override
    protected void onResume() {
        Log.d(LOGTAG, "PlatformActivity onResume");
        super.onResume();
        queueRunnable(activityResumedRunnable);
        setImmersiveSticky();
        if (mPresentation != null)
            mPresentation.resumeGLView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DataReportHelper.reportExit(this);
        //Kaidi，目前的逻辑是当没有检测到Display直接返回，然而此时GLSurfaceView没有初始化；
        //导致queueRunnable(activityDestroyedRunnable)没有发送，这里就会发生死锁！！
        //因此改为当Presentation存在，才运行这段逻辑
        if (mPresentation != null && displayAdded) {
            Log.d(LOGTAG, "mPresentation is not null, queueRunnable activityDestroyRunable");
            synchronized (mRenderLock) {
                queueRunnable(activityDestroyedRunnable);
                try {
                    mRenderLock.wait();
                } catch (InterruptedException e) {
                    Log.e(LOGTAG, "activityDestroyedRunnable interrupted: " + e.toString());
                }
            }
        }

        //Kaidi 先移除Presentation，以避免眼镜上残留GLSurfaceView
        this.releasePresentation();
        this.releaseArcheryDevice();
        this.releaseDisplayListener();
        instance = null;
    }

    void setImmersiveSticky() {
        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    void queueRunnable(Runnable aRunnable) {
        if (mSurfaceCreated) {
            if (mPresentation != null)
                mPresentation.queueEvent(aRunnable);
        } else {
            synchronized (mPendingEvents) {
                mPendingEvents.add(aRunnable);
            }
            if (mSurfaceCreated) {
                notifyPendingEvents();
            }
        }
    }

    private void notifyPendingEvents() {
        synchronized (mPendingEvents) {
            for (Runnable runnable : mPendingEvents) {
                if (mPresentation != null)
                    mPresentation.queueEvent(runnable);
            }
            mPendingEvents.clear();
        }
    }

    //    private float mScale = 0.3f;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    public static volatile boolean produceScrollEvent = false;

    private void setupUI() {
        //Kaidi 暂时先移除之前的操作任务移动的按钮
//        findViewById(R.id.up_button).setOnClickListener((View view) -> dispatchMoveAxis(0, mScale, 0));
//        findViewById(R.id.down_button).setOnClickListener((View view) -> dispatchMoveAxis(0, -mScale, 0));
//        findViewById(R.id.forward_button).setOnClickListener((View view) -> dispatchMoveAxis(0, 0, -mScale));
//        findViewById(R.id.backward_button).setOnClickListener((View view) -> dispatchMoveAxis(0, 0, mScale));
//        findViewById(R.id.left_button).setOnClickListener((View view) -> dispatchMoveAxis(-mScale, 0, 0));
//        findViewById(R.id.right_button).setOnClickListener((View view) -> dispatchMoveAxis(mScale, 0, 0));
//        findViewById(R.id.home_button).setOnClickListener((View view) -> dispatchMoveAxis(0, 0, 0));
//        findViewById(R.id.right_turn_button).setOnClickListener((View view) -> dispatchRotateHeading(-ROTATION * mScale));
//        findViewById(R.id.left_turn_button).setOnClickListener((View view) -> dispatchRotateHeading(ROTATION * mScale));
//        findViewById(R.id.pitch_up_button).setOnClickListener((View view) -> dispatchRotatePitch(ROTATION * mScale));
//        findViewById(R.id.pitch_down_button).setOnClickListener((View view) -> dispatchRotatePitch(-ROTATION * mScale));
//        findViewById(R.id.back_button).setOnClickListener((View view) -> onBackPressed());
//        findViewById(R.id.click_button).setOnTouchListener((View view, MotionEvent event) -> {
//            switch (event.getAction()) {
//                case MotionEvent.ACTION_DOWN:
//                    view.performClick();
//                    buttonClicked(true);
//                    break;
//                case MotionEvent.ACTION_UP:
//                case MotionEvent.ACTION_CANCEL:
//                    buttonClicked(false);
//                    break;
//            }
//            return false;
//        });
        //Kaidi, full screen touchpad
        findViewById(R.id.touch_pad).setOnTouchListener((View view, MotionEvent event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    lastX = event.getX();
                    lastY = event.getY();
                    produceScrollEvent = false;
                    view.performClick();
                    buttonClicked(true);
                    break;
                case MotionEvent.ACTION_UP:
                    //Kaidi，如果触发了滚动操作，那么下次点击事件就需要取消，防止用户滑动结束后误点击。
                    //通过修改WindowWidget.flagCancelClick来实现，强制把下次up事件改为cancel
                    if (produceScrollEvent)
                        WindowWidget.flagCancelClick = true;
                    buttonClicked(false);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    buttonClicked(false);
                    break;
                case MotionEvent.ACTION_MOVE:
                    //Kaidi,处理手指滑动事件；为了避免与点击冲突，设置超过阈值，才开始触发滚动事件
                    final int THRESHOLD = 10;
                    float thisx = event.getX();
                    float thisy = event.getY();
                    if (Math.abs(thisx - downX) >= THRESHOLD || Math.abs(thisy - downY) >= THRESHOLD) {
                        produceScrollEvent = true;
                    }
                    if (produceScrollEvent) {
                        float dx = thisx - lastX;
                        float dy = thisy - lastY;
                        if (PlatformActivity.this instanceof VRBrowserActivity) {
                            VRBrowserActivity activity = (VRBrowserActivity) PlatformActivity.this;
                            activity.dispatchWidgetScrollEvent(dx, dy);
                        }
                    }
                    lastX = thisx;
                    lastY = thisy;
                    break;
            }
            return false;
        });
        findViewById(R.id.home_btn).setOnClickListener(view -> {
            //Kaidi,如果直接finish：在0Dof模式下，会先显示百度页面，然后再消失，给用户的体验不好
            //因此，先选择隐藏Presentation，然后过一段时间finish。这是目前临时的处理方法
            if (mPresentation != null)
                mPresentation.hide();
//            mHandler.postDelayed(this::finish, 200);
            mHandler.postDelayed(() -> {
                finish();
                ProcessSelfKillTool.performSelfKill(this);
            }, 200);
        });
        setImmersiveSticky();
    }

    protected void enter0DofMode() {
        dispatchMoveAxis(0.0f, 0.0f, 0.0f);
        mIs0DofHeading = true;
    }

    protected void enter3DofMode() {
        dispatchMoveAxis(0.0f, 0.0f, 0.0f);
        mIs0DofHeading = false;
    }

    protected void splashFinished() {
        Log.d(LOGTAG, "splashFinished");
        mIsSplashFinished = true;
    }

    protected void showIconsTemplate(String template) {
        Log.d(LOGTAG, "showIconsTemplate() --> template=" + template);
        switch (template) {
            case LauncherIntentKt.ICONS_TEMPLATE_NAVIGATION:
                showNavigationIcons();
                break;
            case LauncherIntentKt.ICONS_TEMPLATE_IMMERSIVE:
                showImmersiveIcons();
                break;
            case LauncherIntentKt.ICONS_TEMPLATE_DEFAULT:
            default:
                showDefaultIcons();
                break;
        }
    }

    private void showNavigationIcons() {
        if (mBinding == null) {
            return;
        }
        mBinding.backButton.setVisibility(View.VISIBLE);
        mBinding.forwardButton.setVisibility(View.VISIBLE);
        mBinding.reloadButton.setVisibility(View.VISIBLE);
        mBinding.homeButton.setVisibility(View.VISIBLE);
        mBinding.homeBtn.setVisibility(View.VISIBLE);
        mBinding.touchPad.setVisibility(View.VISIBLE);
        mBinding.resetButton.setVisibility(View.VISIBLE);
        mBinding.exitImmersive.setVisibility(View.GONE);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mBinding.resetButton.getLayoutParams();
        params.weight = 1f;
        mBinding.resetButton.setLayoutParams(params);
    }

    private void showDefaultIcons() {
        if (mBinding == null) {
            return;
        }
        mBinding.backButton.setVisibility(View.GONE);
        mBinding.forwardButton.setVisibility(View.GONE);
        mBinding.reloadButton.setVisibility(View.GONE);
        mBinding.homeButton.setVisibility(View.GONE);
        mBinding.homeBtn.setVisibility(View.VISIBLE);
        mBinding.touchPad.setVisibility(View.VISIBLE);
        mBinding.resetButton.setVisibility(View.VISIBLE);
        mBinding.exitImmersive.setVisibility(View.GONE);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mBinding.resetButton.getLayoutParams();
        params.weight = 0f;
        mBinding.resetButton.setLayoutParams(params);
    }

    private void showImmersiveIcons() {
        if (mBinding == null) {
            return;
        }
        mBinding.backButton.setVisibility(View.GONE);
        mBinding.forwardButton.setVisibility(View.GONE);
        mBinding.reloadButton.setVisibility(View.GONE);
        mBinding.homeButton.setVisibility(View.GONE);
        mBinding.homeBtn.setVisibility(View.VISIBLE);
        mBinding.touchPad.setVisibility(View.VISIBLE);
        mBinding.resetButton.setVisibility(View.VISIBLE);
        mBinding.exitImmersive.setVisibility(View.VISIBLE);
        mBinding.exitImmersive.setOnClickListener((view) -> {
            ((VRBrowserActivity) this).exitImmersiveSync();
        });
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mBinding.resetButton.getLayoutParams();
        params.weight = 0f;
        mBinding.resetButton.setLayoutParams(params);
    }

    private void updateUI(final int aMode) {
        if (aMode == 0) {
            Log.d(LOGTAG, "Got render mode of Stand Alone");
            if (((VRBrowserActivity) this).getFocusedWindow().isLauncherFullscreenMode()) {
                showNavigationIcons();
            } else {
                showDefaultIcons();
            }
        } else {
            Log.d(LOGTAG, "Got render mode of Immersive");
            showImmersiveIcons();
        }
        setImmersiveSticky();
    }

    private void dispatchMoveAxis(final float aX, final float aY, final float aZ) {
        queueRunnable(() -> moveAxis(aX, aY, aZ));
    }

    private void dispatchRotateHeading(final float aHeading) {
        if (mIs0DofHeading) return;
        queueRunnable(() -> rotateHeading(aHeading));
    }

    private void dispatchRotatePitch(final float aPitch) {
        if (mIs0DofHeading) return;
        queueRunnable(() -> rotatePitch(aPitch));
    }

    private void buttonClicked(final boolean aPressed) {
        queueRunnable(() -> controllerButtonPressed(aPressed));
    }

    @Keep
    @SuppressWarnings("unused")
    private void setRenderMode(final int aMode) {
        runOnUiThread(() -> updateUI(aMode));
    }

    private native void activityCreated(Object aAssetManager);

    private native void updateViewport(int width, int height);

    private native void activityPaused();

    private native void activityResumed();

    private native void activityDestroyed();

    private native void drawGL();

    private native void moveAxis(float aX, float aY, float aZ);

    private native void rotateHeading(float aHeading);

    private native void rotatePitch(float aPitch);

    private native void touchEvent(boolean aDown, float aX, float aY);

    private native void controllerButtonPressed(boolean aDown);

    /*************************
     * Kaidi native methods
     *************************/
    private native void setDeviceQuaternion(float f0, float f1, float f2, float f3);

    private native void setControllerQuaternion(float f0, float f1, float f2, float f3);
}
