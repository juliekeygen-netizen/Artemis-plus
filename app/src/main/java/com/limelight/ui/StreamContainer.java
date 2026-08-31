package com.limelight.ui;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;

import com.limelight.Game;
import com.limelight.SidewaysStreamMode;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.Stereo3DRenderer;

/**
 * Container for the stream render target and its input connection.
 *
 * Normal 2D and 3D modes keep the existing SurfaceView/GLSurfaceView path. The experimental
 * sideways 2D mode uses TextureView because its Surface participates in the View transform
 * hierarchy, allowing video and overlays to share the same 90-degree visual-root rotation.
 */
public class StreamContainer extends FrameLayout implements SurfaceHolder.Callback,
        Stereo3DRenderer.OnSurfaceReadyListener {

    public interface InputCallbacks {
        boolean handleKeyUp(KeyEvent event);
        boolean handleKeyDown(KeyEvent event);
        boolean handleCommitText(CharSequence text);
        boolean handleDeleteSurroundingText(int beforeLength, int afterLength);
        boolean handleFocusChange(boolean hasWindowFocus);
    }

    public enum StreamMode {
        MODE_2D,
        MODE_AI_3D,
        MODE_AI_3D_MOVIE
    }

    private Game game;
    private PreferenceConfiguration prefConfig;
    private Stereo3DRenderer mStereoRenderer;

    private SurfaceView mSurfaceView;
    private TextureView mTextureView;
    private View mRenderView;
    private Surface mTextureSurface;
    private SurfaceTexture mAttachedSurfaceTexture;
    private Surface mCurrentSurface;
    private Runnable onSurfaceAvailable;
    private StreamMode renderMode = null;
    private InputCallbacks mInputCallbacks;
    private boolean commitTextEnabled = false;

    private double desiredAspectRatio;
    private boolean fillDisplay = false;

    private boolean isSurfaceReady = false;

    public StreamContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public void init(Game game, PreferenceConfiguration prefConfig) {
        if (this.game != null) {
            return;
        }

        this.game = game;
        this.prefConfig = prefConfig;
        this.renderMode = mapIntToStreamMode(prefConfig.renderMode);

        Stereo3DRenderer.isMovieMode = renderMode == StreamMode.MODE_AI_3D_MOVIE;

        isSurfaceReady = false;
        mCurrentSurface = null;

        Context context = getContext();
        LayoutParams childParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        if (renderMode == StreamMode.MODE_2D &&
                SidewaysStreamMode.isActive(prefConfig.sidewaysStreamMode)) {
            createSidewaysTextureView(context, childParams);
            return;
        }

        // Preserve the existing SurfaceView path for every non-sideways session.
        mSurfaceView = new SurfaceView(context);
        mRenderView = mSurfaceView;
        addView(mSurfaceView, childParams);

        if (renderMode != StreamMode.MODE_2D) {
            GLSurfaceView glSurfaceView = new GLSurfaceView(context);
            glSurfaceView.setEGLContextClientVersion(3);
            mStereoRenderer = new Stereo3DRenderer(glSurfaceView, this, context, prefConfig);
            glSurfaceView.setRenderer(mStereoRenderer);
            glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            mSurfaceView = glSurfaceView;
            mRenderView = glSurfaceView;
            addView(mSurfaceView, childParams);
        }

        mSurfaceView.getHolder().addCallback(this);
        if (mSurfaceView.getHolder().getSurface() != null &&
                mSurfaceView.getHolder().getSurface().isValid()) {
            surfaceChanged(mSurfaceView.getHolder(), PixelFormat.RGBA_8888,
                    mSurfaceView.getWidth(), mSurfaceView.getHeight());
        }
    }

    private void createSidewaysTextureView(Context context, LayoutParams childParams) {
        mTextureView = new TextureView(context);
        mRenderView = mTextureView;
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                attachSidewaysTexture(surfaceTexture, width, height);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
                if (mCurrentSurface != null && mCurrentSurface.isValid()) {
                    game.streamSurfaceChanged(PixelFormat.RGBA_8888, width, height);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                // Ignore a stale destroy callback for a producer that is no longer our active
                // render target. This is defensive against vendor TextureView callback ordering.
                if (mAttachedSurfaceTexture != surfaceTexture) {
                    return true;
                }
                isSurfaceReady = false;
                if (mCurrentSurface != null) {
                    game.streamSurfaceDestroyed();
                }
                mCurrentSurface = null;
                closeTextureSurface();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            }
        });
        addView(mTextureView, childParams);

        if (mTextureView.isAvailable() && mTextureView.getSurfaceTexture() != null) {
            attachSidewaysTexture(mTextureView.getSurfaceTexture(),
                    mTextureView.getWidth(), mTextureView.getHeight());
        }
    }

    /**
     * Attach one TextureView producer exactly once. Some vendor implementations can make the
     * TextureView already available during init and still deliver an availability callback.
     * Releasing/re-wrapping that same SurfaceTexture after MediaCodec starts would risk replacing
     * the Java Surface wrapper underneath a live decoder, so duplicate callbacks are idempotent.
     */
    private void attachSidewaysTexture(SurfaceTexture surfaceTexture, int width, int height) {
        if (surfaceTexture == null) {
            return;
        }
        if (mAttachedSurfaceTexture == surfaceTexture && mTextureSurface != null &&
                mTextureSurface.isValid()) {
            mCurrentSurface = mTextureSurface;
            if (!isSurfaceReady) {
                notifySurfaceReady();
            }
            if (width > 0 && height > 0) {
                game.streamSurfaceChanged(PixelFormat.RGBA_8888, width, height);
            }
            return;
        }

        // A genuinely different producer should normally arrive only after the previous destroy
        // callback. If a vendor skips that callback, explicitly end the old visible-surface
        // lifecycle before releasing our wrapper so Fast Resume/Keep Alive can transition safely.
        if (mCurrentSurface != null) {
            isSurfaceReady = false;
            game.streamSurfaceDestroyed();
            mCurrentSurface = null;
        }
        closeTextureSurface();
        mAttachedSurfaceTexture = surfaceTexture;
        mTextureSurface = new Surface(surfaceTexture);
        mCurrentSurface = mTextureSurface;
        game.streamSurfaceCreated(mTextureSurface);
        notifySurfaceReady();
        if (width > 0 && height > 0) {
            game.streamSurfaceChanged(PixelFormat.RGBA_8888, width, height);
        }
    }

    // --- Aspect Ratio and Scaling Logic ---
    public void setDesiredAspectRatio(double aspectRatio) {
        this.desiredAspectRatio = aspectRatio;
        requestLayout();
    }

    public void setFillDisplay(boolean fillDisplay) {
        this.fillDisplay = fillDisplay;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (renderMode != StreamMode.MODE_2D) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        if (desiredAspectRatio == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int measuredHeight, measuredWidth;

        if (fillDisplay) {
            if (widthSize < heightSize * desiredAspectRatio) {
                measuredHeight = heightSize;
                measuredWidth = (int)(heightSize * desiredAspectRatio);
            } else {
                measuredWidth = widthSize;
                measuredHeight = (int)(widthSize / desiredAspectRatio);
            }
        } else {
            if (widthSize > heightSize * desiredAspectRatio) {
                measuredHeight = heightSize;
                measuredWidth = (int)(measuredHeight * desiredAspectRatio);
            } else {
                measuredWidth = widthSize;
                measuredHeight = (int)(measuredWidth / desiredAspectRatio);
            }
        }

        setMeasuredDimension(measuredWidth, measuredHeight);
        int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY);
        int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY);
        measureChildren(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    public void setInputCallbacks(InputCallbacks callbacks) {
        this.mInputCallbacks = callbacks;
    }

    public void setCommitTextEnabled(boolean enabled) {
        this.commitTextEnabled = enabled;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (mInputCallbacks != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (mInputCallbacks.handleKeyDown(event)) return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (mInputCallbacks.handleKeyUp(event)) return true;
            }
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (mInputCallbacks != null) {
            mInputCallbacks.handleFocusChange(hasWindowFocus);
        }
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return commitTextEnabled || super.onCheckIsTextEditor();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (!commitTextEnabled) {
            return super.onCreateInputConnection(outAttrs);
        }
        outAttrs.inputType = android.text.InputType.TYPE_CLASS_TEXT;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        return new BaseInputConnection(this, false) {
            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                return mInputCallbacks != null && mInputCallbacks.handleCommitText(text) ||
                        super.commitText(text, newCursorPosition);
            }

            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                return mInputCallbacks != null &&
                        mInputCallbacks.handleDeleteSurroundingText(beforeLength, afterLength) ||
                        super.deleteSurroundingText(beforeLength, afterLength);
            }
        };
    }

    public void setOnSurfaceAvailable(Runnable callback) {
        this.onSurfaceAvailable = callback;
        if (isSurfaceReady && onSurfaceAvailable != null) {
            onSurfaceAvailable.run();
        }
    }

    public Surface getSurface() {
        return mCurrentSurface;
    }

    /** Existing accessor retained for normal SurfaceView/GLSurfaceView callers. */
    public SurfaceView getSurfaceView() {
        return mSurfaceView;
    }

    /** Render View used for pan/zoom. TextureView is returned in sideways 2D mode. */
    public View getRenderView() {
        return mRenderView;
    }

    public StreamMode mapIntToStreamMode(int modeIndex) {
        StreamContainer.StreamMode[] modes = StreamContainer.StreamMode.values();
        if (modeIndex >= 0 && modeIndex < modes.length) {
            return modes[modeIndex];
        } else {
            return StreamContainer.StreamMode.MODE_2D;
        }
    }

    private void notifySurfaceReady() {
        isSurfaceReady = true;
        if (onSurfaceAvailable != null) {
            onSurfaceAvailable.run();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        game.surfaceCreated(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (renderMode == StreamMode.MODE_2D && width > 0 && height > 0) {
            mCurrentSurface = holder.getSurface();
            notifySurfaceReady();
        }

        game.surfaceChanged(holder, format, width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (renderMode == StreamMode.MODE_2D) {
            isSurfaceReady = false;
            mCurrentSurface = null;
        } else if (mStereoRenderer != null) {
            mStereoRenderer.onSurfaceDestroyed();
        }

        game.surfaceDestroyed(holder);
    }

    @Override
    public void onStereo3DSurfaceReady(Surface surface) {
        if (renderMode != StreamMode.MODE_2D) {
            mCurrentSurface = surface;
            notifySurfaceReady();
        }
    }

    private void closeTextureSurface() {
        if (mTextureSurface != null) {
            try {
                mTextureSurface.release();
            } catch (RuntimeException ignored) {
            }
            mTextureSurface = null;
        }
        mAttachedSurfaceTexture = null;
    }

    public void onDestroy() {
        if (mStereoRenderer != null) {
            mStereoRenderer.onSurfaceDestroyed();
        }
        closeTextureSurface();
        mCurrentSurface = null;
        isSurfaceReady = false;
    }
}
