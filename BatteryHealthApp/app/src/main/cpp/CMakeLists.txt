cmake_minimum_required(VERSION 3.18.1)
project("batteryhealth")

# 设置 C++ 标准
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 添加编译选项
add_compile_options(-Wall -Wextra -O2)

# 定义源文件
set(CORE_SOURCES
    battery_health_jni.cpp
    battery_health.cpp
    battery_models.cpp
    bugreport_parser.cpp
    sn_decoder.cpp
    zip_parser.cpp
)

# 创建共享库
add_library(batteryhealth SHARED ${CORE_SOURCES})

# 包含头文件目录
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# 链接库
target_link_libraries(batteryhealth
    log     # Android log library
    z       # zlib for ZIP decompression
)