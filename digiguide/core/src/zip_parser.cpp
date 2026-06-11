#include "zip_parser.h"
#include <fstream>
#include <sstream>
#include <algorithm>

// libzip 头文件（需要安装 libzip）
// #include <zip.h>

namespace digiguide::core {

// ========== ZIP解析实现 ==========

ZipParseResult ZipParser::parseFromFile(const std::string& zip_path) {
    ZipParseResult result;
    result.success = false;
    result.total_files_extracted = 0;
    result.nested_zips_processed = 0;

    // 检查文件是否存在
    std::ifstream file(zip_path, std::ios::binary);
    if (!file.is_open()) {
        result.error_message = "无法打开ZIP文件: " + zip_path;
        return result;
    }

    // 读取文件内容到内存
    std::vector<uint8_t> data((std::istreambuf_iterator<char>(file)),
                               std::istreambuf_iterator<char>());
    file.close();

    // 从内存解析
    result = parseFromMemory(data);

    return result;
}

ZipParseResult ZipParser::parseFromMemory(const std::vector<uint8_t>& data) {
    ZipParseResult result;
    result.success = false;
    result.total_files_extracted = 0;
    result.nested_zips_processed = 0;

    // TODO: 使用 libzip 实现完整的 ZIP 解析
    // 这里提供简化实现，实际项目需要集成 libzip

    // 简化实现：假设数据是文本格式（用于测试）
    std::string text_content(data.begin(), data.end());

    // 检查是否包含 bugreport 关键字
    if (text_content.find("bugreport") != std::string::npos ||
        text_content.find("ro.product.brand") != std::string::npos) {

        result.files.emplace_back("bugreport.txt", text_content);
        result.main_bugreport_content = text_content;
        result.total_files_extracted = 1;
        result.success = true;
    } else {
        result.error_message = "ZIP内容不是有效的bugreport格式";
    }

    return result;
}

ZipParseResult ZipParser::parseNested(const std::string& parent_content) {
    ZipParseResult result;
    result.success = false;

    // 检查是否包含嵌套ZIP（通过文件名或内容特征）
    if (parent_content.find(".zip") != std::string::npos) {
        // TODO: 实现嵌套ZIP解析
        result.nested_zips_processed = 0;
    }

    return result;
}

std::optional<std::string> ZipParser::findBugreportFile(
    const std::vector<std::pair<std::string, std::string>>& files) {

    // 查找主 bugreport 文件
    for (const auto& [name, content] : files) {
        // 检查文件名
        if (name.find("bugreport") != std::string::npos) {
            return content;
        }

        // 检查内容特征
        if (content.find("ro.product.brand") != std::string::npos ||
            content.find("battery") != std::string::npos) {
            return content;
        }
    }

    return std::nullopt;
}

bool ZipParser::extractZip(const std::string& path,
                           std::vector<std::pair<std::string, std::string>>& output) {

    // TODO: 使用 libzip 实现
    // 伪代码：
    // zip_t* zip = zip_open(path.c_str(), 0, NULL);
    // if (!zip) return false;
    //
    // int num_entries = zip_get_num_entries(zip, 0);
    // for (int i = 0; i < num_entries; i++) {
    //     zip_stat_t stat;
    //     zip_stat_index(zip, i, 0, &stat);
    //
    //     zip_file_t* file = zip_fopen_index(zip, i, 0);
    //     std::string content(stat.size, '\0');
    //     zip_fread(file, content.data(), stat.size);
    //     zip_fclose(file);
    //
    //     output.emplace_back(stat.name, content);
    // }
    //
    // zip_close(zip);
    // return true;

    return false;  // 简化实现返回失败
}

void ZipParser::processNestedZips(ZipParseResult& result) {
    // TODO: 实现嵌套ZIP处理
    // 递归解析嵌套的ZIP文件
}

} // namespace digiguide::core