cmake_minimum_required(VERSION 3.22.1)
project(digiguide_jni VERSION 3.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 添加C++ Core引擎源文件路径
set(CORE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../core)

# 源文件
set(JNI_SOURCES
    jni_bridge.cpp
    ${CORE_DIR}/src/sn_decoder.cpp
    ${CORE_DIR}/src/bugreport_parser.cpp
    ${CORE_DIR}/src/battery_health.cpp
    ${CORE_DIR}/src/battery_models.cpp
    ${CORE_DIR}/src/zip_parser.cpp
)

# 头文件目录
include_directories(
    ${CORE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# 创建JNI共享库
add_library(digiguide_jni SHARED ${JNI_SOURCES})

# 链接Android日志库
find_library(log-lib log)
target_link_libraries(digiguide_jni ${log-lib})

# 编译选项
target_compile_options(digiguide_jni PRIVATE
    -Wall
    -Wextra
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
)