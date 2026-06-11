cmake_minimum_required(VERSION 3.16)
project(digiguide_core VERSION 3.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 编译选项
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra")

# 源文件
set(CORE_SOURCES
    src/sn_decoder.cpp
    src/bugreport_parser.cpp
    src/battery_health.cpp
    src/battery_models.cpp
    src/zip_parser.cpp
)

# 头文件目录
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

# 核心库
add_library(digiguide_core STATIC ${CORE_SOURCES})

# 链接依赖（libzip用于ZIP解析）
find_package(ZLIB QUIET)
if(ZLIB_FOUND)
    target_link_libraries(digiguide_core ZLIB::ZLIB)
endif()

# 测试
option(BUILD_TESTS "Build unit tests" ON)
if(BUILD_TESTS)
    enable_testing()

    # SN解码测试
    add_executable(test_sn_decoder tests/test_sn_decoder.cpp)
    target_link_libraries(test_sn_decoder digiguide_core)
    add_test(NAME SNDecoderTest COMMAND test_sn_decoder)

    # Bugreport解析测试
    add_executable(test_bugreport tests/test_bugreport.cpp)
    target_link_libraries(test_bugreport digiguide_core)
    add_test(NAME BugreportParserTest COMMAND test_bugreport)

    # 健康度计算测试
    add_executable(test_health tests/test_health.cpp)
    target_link_libraries(test_health digiguide_core)
    add_test(NAME HealthCalculatorTest COMMAND test_health)
endif()

# 安装
install(TARGETS digiguide_core DESTINATION lib)
install(DIRECTORY include/ DESTINATION include/digiguide)