package com.batteryhealth.app.utils;

import android.opengl.GLES20;
import android.util.Log;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * GPU 离屏渲染基准测试。
 *
 * 通过 EGL 创建 PBuffer 离屏表面和 OpenGL ES 2.0 上下文，
 * 编译着色器并渲染纹理四边形多帧，测量实际 GPU 渲染 FPS。
 * 不需要 Activity 或 SurfaceView，可在任意工作线程运行。
 */
public class GpuBenchmark {

    private static final String TAG = "GpuBenchmark";

    private static final int PBUFFER_WIDTH = 256;
    private static final int PBUFFER_HEIGHT = 256;
    private static final int FRAME_COUNT = 100;

    private static final String VERTEX_SHADER =
            "attribute vec4 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "    gl_Position = aPosition;\n" +
            "    vTexCoord = aTexCoord;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "varying vec2 vTexCoord;\n" +
            "uniform sampler2D uTexture;\n" +
            "void main() {\n" +
            "    vec4 c = texture2D(uTexture, vTexCoord);\n" +
            "    gl_FragColor = c * 0.5 + 0.5 * vec4(vTexCoord, 0.0, 1.0);\n" +
            "}\n";

    public static class Result {
        public final long fps;
        public final long score;

        public Result(long fps, long score) {
            this.fps = fps;
            this.score = score;
        }
    }

    /**
     * 执行 GPU 离屏渲染基准测试。必须在非 UI 线程调用。
     *
     * @return 测试结果，如果 GPU 初始化失败则 fps=0, score=0
     */
    public static Result run() {
        EGL10 egl = null;
        EGLDisplay eglDisplay = null;
        EGLSurface eglSurface = null;
        EGLContext eglContext = null;

        try {
            egl = (EGL10) EGLContext.getEGL();
            eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
                Log.w(TAG, "eglGetDisplay failed");
                return new Result(0, 0);
            }

            int[] version = new int[2];
            if (!egl.eglInitialize(eglDisplay, version)) {
                Log.w(TAG, "eglInitialize failed");
                return new Result(0, 0);
            }

            // 选择 EGL 配置：OpenGL ES 2.0
            int[] configAttribs = {
                    EGL10.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
                    EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!egl.eglChooseConfig(eglDisplay, configAttribs, configs, 1, numConfigs)
                    || numConfigs[0] == 0) {
                Log.w(TAG, "eglChooseConfig failed");
                egl.eglTerminate(eglDisplay);
                return new Result(0, 0);
            }
            EGLConfig eglConfig = configs[0];

            // 创建 PBuffer 表面
            int[] pbufferAttribs = {
                    EGL10.EGL_WIDTH, PBUFFER_WIDTH,
                    EGL10.EGL_HEIGHT, PBUFFER_HEIGHT,
                    EGL10.EGL_NONE
            };
            eglSurface = egl.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs);
            if (eglSurface == EGL10.EGL_NO_SURFACE) {
                Log.w(TAG, "eglCreatePbufferSurface failed");
                egl.eglTerminate(eglDisplay);
                return new Result(0, 0);
            }

            // 创建 OpenGL ES 2.0 上下文
            int[] contextAttribs = {
                    0x3098, 2, // EGL_CONTEXT_CLIENT_VERSION, 2
                    EGL10.EGL_NONE
            };
            eglContext = egl.eglCreateContext(eglDisplay, eglConfig,
                    EGL10.EGL_NO_CONTEXT, contextAttribs);
            if (eglContext == EGL10.EGL_NO_CONTEXT) {
                Log.w(TAG, "eglCreateContext failed");
                egl.eglDestroySurface(eglDisplay, eglSurface);
                egl.eglTerminate(eglDisplay);
                return new Result(0, 0);
            }

            // 绑定上下文
            if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                Log.w(TAG, "eglMakeCurrent failed");
                cleanup(egl, eglDisplay, eglSurface, eglContext);
                return new Result(0, 0);
            }

            // 编译着色器和链接程序
            int program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            if (program == 0) {
                Log.w(TAG, "Shader program creation failed");
                cleanup(egl, eglDisplay, eglSurface, eglContext);
                return new Result(0, 0);
            }

            // 创建纹理
            int texture = createTexture();

            // 设置顶点数据（全屏四边形）
            FloatBuffer vertexBuffer = createQuadVertices();
            ShortBuffer indexBuffer = createQuadIndices();

            // 配置渲染状态
            GLES20.glViewport(0, 0, PBUFFER_WIDTH, PBUFFER_HEIGHT);
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glUseProgram(program);

            int aPosition = GLES20.glGetAttribLocation(program, "aPosition");
            int aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord");
            int uTexture = GLES20.glGetUniformLocation(program, "uTexture");

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glUniform1i(uTexture, 0);

            GLES20.glEnableVertexAttribArray(aPosition);
            GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer);
            GLES20.glEnableVertexAttribArray(aTexCoord);
            GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 8,
                    createTexCoordBuffer());

            // 渲染循环：测量 FPS
            long startTime = System.nanoTime();
            for (int i = 0; i < FRAME_COUNT; i++) {
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, indexBuffer);
                egl.eglSwapBuffers(eglDisplay, eglSurface);
            }
            // 确保 GPU 完成所有渲染命令
            GLES20.glFinish();
            long elapsed = System.nanoTime() - startTime;

            long fps = elapsed > 0 ? (long) (FRAME_COUNT * 1_000_000_000.0 / elapsed) : 0;

            // 清理 GLES 资源
            GLES20.glDisableVertexAttribArray(aPosition);
            GLES20.glDisableVertexAttribArray(aTexCoord);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glUseProgram(0);
            GLES20.glDeleteProgram(program);
            GLES20.glDeleteTextures(1, new int[]{texture}, 0);

            // 清理 EGL 资源
            cleanup(egl, eglDisplay, eglSurface, eglContext);

            long score = mapGpuScore(fps);
            Log.d(TAG, "GPU benchmark: " + fps + " fps, score=" + score);
            return new Result(fps, score);

        } catch (Exception e) {
            Log.w(TAG, "GPU benchmark failed", e);
            try {
                cleanup(egl, eglDisplay, eglSurface, eglContext);
            } catch (Exception ignored) {
            }
            return new Result(0, 0);
        }
    }

    private static void cleanup(EGL10 egl, EGLDisplay display, EGLSurface surface, EGLContext context) {
        if (egl == null || display == null) return;
        egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
        if (surface != null) egl.eglDestroySurface(display, surface);
        if (context != null) egl.eglDestroyContext(display, context);
        egl.eglTerminate(display);
    }

    private static int loadShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) return 0;
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.w(TAG, "Shader compile error: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) return 0;
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader);
            return 0;
        }
        int program = GLES20.glCreateProgram();
        if (program == 0) {
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
            return 0;
        }
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        GLES20.glDetachShader(program, vertexShader);
        GLES20.glDetachShader(program, fragmentShader);
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
        if (linked[0] == 0) {
            Log.w(TAG, "Program link error: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    private static int createTexture() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        int texture = textures[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // 生成 64x64 棋盘格纹理
        int texSize = 64;
        int[] pixels = new int[texSize * texSize];
        for (int y = 0; y < texSize; y++) {
            for (int x = 0; x < texSize; x++) {
                boolean isWhite = ((x / 8) + (y / 8)) % 2 == 0;
                pixels[y * texSize + x] = isWhite ? 0xFFFFFFFF : 0xFF333333;
            }
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(pixels.length * 4);
        buf.order(ByteOrder.nativeOrder());
        buf.asIntBuffer().put(pixels);
        buf.position(0);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, texSize, texSize, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return texture;
    }

    private static FloatBuffer createQuadVertices() {
        // 位置 (x, y, z) + 纹理坐标 (u, v) 分开存储
        float[] vertices = {
                -1.0f, -1.0f, 0.0f,
                 1.0f, -1.0f, 0.0f,
                 1.0f,  1.0f, 0.0f,
                -1.0f,  1.0f, 0.0f,
        };
        FloatBuffer buf = ByteBuffer.allocateDirect(vertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buf.put(vertices);
        buf.position(0);
        return buf;
    }

    private static FloatBuffer createTexCoordBuffer() {
        float[] coords = {
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f,
        };
        FloatBuffer buf = ByteBuffer.allocateDirect(coords.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buf.put(coords);
        buf.position(0);
        return buf;
    }

    private static ShortBuffer createQuadIndices() {
        short[] indices = {0, 1, 2, 0, 2, 3};
        ShortBuffer buf = ByteBuffer.allocateDirect(indices.length * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
        buf.put(indices);
        buf.position(0);
        return buf;
    }

    private static long mapGpuScore(long fps) {
        return fps * 17;
    }
}
