# 数码指南 APP

面向工程交付的完整技术实现方案。

## 项目结构

```
digiguide/
├── core/                          # C++ 跨平台核心引擎
│   ├── include/                   # 头文件
│   ├── src/                       # 源文件
│   ├── tests/                     # 单元测试
│   └── CMakeLists.txt
│
├── android/                       # Android客户端
│   ├── app/                       # 应用模块
│   ├── cpp/                       # JNI桥接
│   └── build.gradle.kts
│
├── backend/                       # Go后端服务
│   ├── cmd/server/                # 服务入口
│   ├── internal/                  # 内部模块
│   ├── migrations/                # 数据库迁移
│   └── Dockerfile
│
└── docs/                          # 文档
    ├── api_spec.yaml              # OpenAPI 3.0
    └── sn_encoding_reference.md   # SN编码参考
```

## 功能特性

### SN序列号查询
- 支持12个主要品牌：Apple、Samsung、华为、荣耀、小米、OPPO、vivo、联想、惠普、华硕、戴尔
- 自动品牌识别
- 生产日期解码
- 保修状态估算

### 电池健康度分析
- bugreport文件解析
- 多维度健康度计算（容量保持率、循环衰减、内阻增长、温度老化、充电损伤）
- A+至F等级评定
- 使用建议生成

## 技术栈

- **C++ Core引擎**：C++17，CMake，libzip
- **Android客户端**：Jetpack Compose，Kotlin，Room，Retrofit
- **Go后端**：Gin框架，GORM，PostgreSQL/MySQL

## 编译说明

### C++ Core引擎
```bash
cd core
cmake -B build
cmake --build build
ctest --test-dir build
```

### Android客户端
```bash
cd android
./gradlew assembleRelease
```

### Go后端
```bash
cd backend
go build -o server ./cmd/server
```

## API文档

详见 [api_spec.yaml](docs/api_spec.yaml)

## 版本

v3.1.0