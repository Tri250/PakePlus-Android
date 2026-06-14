#pragma once

#include <string>
#include <vector>
#include <optional>
#include <cstdint>

namespace digiguide::core {

// ZIP解析结果
struct ZipParseResult {
    bool success;
    std::string error_message;

    // 解析出的文件内容
    std::vector<std::pair<std::string, std::string>> files;  // 文件名 -> 内容

    // 嵌套ZIP处理结果
    std::vector<ZipParseResult> nested_results;

    // 主要bugreport文件内容
    std::optional<std::string> main_bugreport_content;

    // 辅助信息
    int total_files_extracted;
    int nested_zips_processed;
};

// ZIP解析器类
class ZipParser {
public:
    // 从文件路径解析ZIP
    static ZipParseResult parseFromFile(const std::string& zip_path);

    // 从内存数据解析ZIP
    static ZipParseResult parseFromMemory(const std::vector<uint8_t>& data);

    // 解析嵌套ZIP（bugreport可能包含嵌套ZIP）
    static ZipParseResult parseNested(const std::string& parent_content);

    // 查找bugreport主文件
    static std::optional<std::string> findBugreportFile(
        const std::vector<std::pair<std::string, std::string>>& files);

private:
    // ZIP 解压实现（使用 zlib）
    static std::string decompressDeflate(const uint8_t* compressed_data,
                                          uint32_t compressed_size,
                                          uint32_t uncompressed_size);

    // libzip封装实现
    static bool extractZip(const std::string& path,
                           std::vector<std::pair<std::string, std::string>>& output);

    // 递归处理嵌套ZIP
    static void processNestedZips(ZipParseResult& result);
};

} // namespace digiguide::core