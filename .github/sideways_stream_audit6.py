from pathlib import Path

path = Path('app/src/main/java/com/limelight/ui/StreamContainer.java')
text = path.read_text()

text = text.replace(
'''    private Surface mTextureSurface;\n    private Surface mCurrentSurface;''',
'''    private Surface mTextureSurface;\n    private SurfaceTexture mAttachedSurfaceTexture;\n    private Surface mCurrentSurface;''')

text = text.replace(
'''            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {\n                closeTextureSurface();\n                mTextureSurface = new Surface(surfaceTexture);\n                mCurrentSurface = mTextureSurface;\n                game.streamSurfaceCreated(mTextureSurface);\n                notifySurfaceReady();\n                game.streamSurfaceChanged(PixelFormat.RGBA_8888, width, height);\n            }''',
'''            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {\n                attachSidewaysTexture(surfaceTexture, width, height);\n            }''')

text = text.replace(
'''            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {\n                isSurfaceReady = false;\n                if (mCurrentSurface != null) {\n                    game.streamSurfaceDestroyed();\n                }\n                mCurrentSurface = null;\n                closeTextureSurface();\n                return true;\n            }''',
'''            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {\n                // Ignore a stale destroy callback for a producer that is no longer our active\n                // render target. This is defensive against vendor TextureView callback ordering.\n                if (mAttachedSurfaceTexture != surfaceTexture) {\n                    return true;\n                }\n                isSurfaceReady = false;\n                if (mCurrentSurface != null) {\n                    game.streamSurfaceDestroyed();\n                }\n                mCurrentSurface = null;\n                closeTextureSurface();\n                return true;\n            }''')

text = text.replace(
'''        if (mTextureView.isAvailable() && mTextureView.getSurfaceTexture() != null) {\n            SurfaceTexture texture = mTextureView.getSurfaceTexture();\n            mTextureSurface = new Surface(texture);\n            mCurrentSurface = mTextureSurface;\n            game.streamSurfaceCreated(mTextureSurface);\n            notifySurfaceReady();\n            game.streamSurfaceChanged(PixelFormat.RGBA_8888,\n                    mTextureView.getWidth(), mTextureView.getHeight());\n        }\n    }\n\n    // --- Aspect Ratio and Scaling Logic ---''',
'''        if (mTextureView.isAvailable() && mTextureView.getSurfaceTexture() != null) {\n            attachSidewaysTexture(mTextureView.getSurfaceTexture(),\n                    mTextureView.getWidth(), mTextureView.getHeight());\n        }\n    }\n\n    /**\n     * Attach one TextureView producer exactly once. Some vendor implementations can make the\n     * TextureView already available during init and still deliver an availability callback.\n     * Releasing/re-wrapping that same SurfaceTexture after MediaCodec starts would risk replacing\n     * the Java Surface wrapper underneath a live decoder, so duplicate callbacks are idempotent.\n     */\n    private void attachSidewaysTexture(SurfaceTexture surfaceTexture, int width, int height) {\n        if (surfaceTexture == null) {\n            return;\n        }\n        if (mAttachedSurfaceTexture == surfaceTexture && mTextureSurface != null &&\n                mTextureSurface.isValid()) {\n            mCurrentSurface = mTextureSurface;\n            if (!isSurfaceReady) {\n                notifySurfaceReady();\n            }\n            if (width > 0 && height > 0) {\n                game.streamSurfaceChanged(PixelFormat.RGBA_8888, width, height);\n            }\n            return;\n        }\n\n        // A genuinely different producer should normally arrive only after the previous destroy\n        // callback. If a vendor skips that callback, explicitly end the old visible-surface\n        // lifecycle before releasing our wrapper so Fast Resume/Keep Alive can transition safely.\n        if (mCurrentSurface != null) {\n            isSurfaceReady = false;\n            game.streamSurfaceDestroyed();\n            mCurrentSurface = null;\n        }\n        closeTextureSurface();\n        mAttachedSurfaceTexture = surfaceTexture;\n        mTextureSurface = new Surface(surfaceTexture);\n        mCurrentSurface = mTextureSurface;\n        game.streamSurfaceCreated(mTextureSurface);\n        notifySurfaceReady();\n        if (width > 0 && height > 0) {\n            game.streamSurfaceChanged(PixelFormat.RGBA_8888, width, height);\n        }\n    }\n\n    // --- Aspect Ratio and Scaling Logic ---''')

text = text.replace(
'''            mTextureSurface = null;\n        }\n    }''',
'''            mTextureSurface = null;\n        }\n        mAttachedSurfaceTexture = null;\n    }''')

path.write_text(text)
