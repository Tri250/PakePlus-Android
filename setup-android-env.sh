#!/bin/bash
# Android SDK environment setup
export ANDROID_HOME=/workspace/android-sdk
export ANDROID_SDK_ROOT=/workspace/android-sdk
export JAVA_HOME=/root/.local/share/mise/installs/java/17.0.2
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/36.0.0:$JAVA_HOME/bin:$PATH

# 当前环境需要通过本地代理 127.0.0.1:18080 访问外网，Gradle/网络库统一走代理
export http_proxy=http://127.0.0.1:18080
export https_proxy=http://127.0.0.1:18080
export HTTP_PROXY=http://127.0.0.1:18080
export HTTPS_PROXY=http://127.0.0.1:18080
export NO_PROXY=localhost,127.0.0.1
export no_proxy=localhost,127.0.0.1
