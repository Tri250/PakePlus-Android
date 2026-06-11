package com.digiguide.core

/**
 * Native库加载器
 * 负责加载C++ Core引擎的JNI库
 */
object NativeLib {

    private var isLoaded = false

    /**
     * 初始化Native库
     */
    fun init() {
        if (!isLoaded) {
            try {
                System.loadLibrary("digiguide_jni")
                isLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                // 如果JNI库不存在，使用纯Kotlin实现
                println("JNI library not found, using Kotlin fallback: ${e.message}")
            }
        }
    }

    /**
     * 检查Native库是否已加载
     */
    fun isNativeLoaded(): Boolean = isLoaded
}