package com.igalia.wolvic;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Display;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;

public class MyPresentation extends Presentation {

    private Context context;
    private IActivityCallback mActivityCallback;
    private GLSurfaceView mGLView;

    public MyPresentation(Activity activity, Display display) {
        super(activity, display);
        this.context = activity;
        this.mActivityCallback = (IActivityCallback) activity;
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.setContentView(R.layout.presentation_browser);
        this.mGLView = findViewById(R.id.gl_view);
        this.initGLView();
    }

    @Override
    public void dismiss() {
        super.dismiss();
        mGLView = null;
    }

    public void queueEvent(Runnable runnable) {
        if (mGLView != null)
            mGLView.queueEvent(runnable);
    }

    public void pauseGLView() {
        if (mGLView != null)
            mGLView.onPause();
    }

    public void resumeGLView() {
        if (mGLView != null)
            mGLView.onResume();
    }

    private void initGLView() {
        mGLView.setEGLContextClientVersion(3);
        mGLView.setEGLConfigChooser(new MyConfigChooser());
//        mGLView.setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        mGLView.setRenderer(mActivityCallback.getRenderer());
    }

    /**
     * Kaidi,开启多重采样，防止浏览器窗口锯齿<br/>
     * 需要注意的是，多重采样对性能有影响，以后也许需要调整EGL_SAMPLES这个数值</br>
     * <p/>
     * 另外，在设置-显示中的MASS是针对WebGL内容的，而不是对浏览器本身，因此那个选项并不影响浏览器窗口的锯齿<br/>
     * 但是会影响到那些使用WebGL的网站的渲染质量
     */
    private static class MyConfigChooser implements GLSurfaceView.EGLConfigChooser {
        @Override
        public EGLConfig chooseConfig(EGL10 egl,
                                      javax.microedition.khronos.egl.EGLDisplay display) {

            int[] attribs = {
                    EGL10.EGL_LEVEL, 0,
                    EGL10.EGL_RENDERABLE_TYPE, 4,  // EGL_OPENGL_ES2_BIT
                    EGL10.EGL_COLOR_BUFFER_TYPE, EGL10.EGL_RGB_BUFFER,
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 16,
                    EGL10.EGL_SAMPLE_BUFFERS, 1,
                    EGL10.EGL_SAMPLES, 2,  // 在这里修改MSAA的倍数，4就是4xMSAA，再往上开程序可能会崩
                    EGL10.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] configCounts = new int[1];
            egl.eglChooseConfig(display, attribs, configs, 1, configCounts);

            if (configCounts[0] == 0) {
                // Failed! Error handling.
                return null;
            } else {
                return configs[0];
            }
        }
    }
}
