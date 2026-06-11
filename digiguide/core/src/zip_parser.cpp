#include "zip_parser.h"
#include "bugreport_parser.h"
#include <fstream>
#include <sstream>
#include <algorithm>
#include <cstring>
#include <filesystem>

// libzip 头文件（需要安装 libzip）
#ifdef USE_LIBZIP
#include <zip.h>
#endif

namespace digiguide::core {

// ========== 辅助函数 ==========

namespace {

// 检查字符串是否以指定后缀结尾
bool ends_with(const std::string& str, const std::string& suffix) {
    if (suffix.size() > str.size()) return false;
    return std::equal(suffix.rbegin(), suffix.rend(), str.rbegin(),
                      [](char a, char b) { return std::tolower(a) == std::tolower(b); });
}

// 检查字符串是否包含子串（忽略大小写）
bool contains_ignore_case(const std::string& str, const std::string& substr) {
    auto it = std::search(str.begin(), str.end(), substr.begin(), substr.end(),
                          [](char a, char b) { return std::tolower(a) == std::tolower(b); });
    return it != str.end();
}

#ifdef USE_LIBZIP

// Zip异常类
class ZipError : public std::runtime_error {
public:
    explicit ZipError(const std::string& msg) : std::runtime_error(msg) {}
};

// 从ZIP条目读取文本内容
std::string read_entry_text(zip_t* archive, zip_int64_t index) {
    zip_stat_t stat;
    zip_stat_init(&stat);
    if (zip_stat_index(archive, index, 0, &stat) != 0) {
        throw ZipError("无法获取ZIP条目信息");
    }

    zip_file_t* file = zip_fopen_index(archive, index, 0);
    if (!file) {
        throw ZipError("无法打开ZIP条目");
    }

    std::string content(stat.size, '\0');
    zip_int64_t bytes_read = zip_fread(file, content.data(), stat.size);
    zip_fclose(file);

    if (bytes_read != static_cast<zip_int64_t>(stat.size)) {
        throw ZipError("读取ZIP条目不完整");
    }

    return content;
}

// 将ZIP条目解压到临时文件
std::string extract_entry_to_temp(zip_t* archive, zip_int64_t index) {
    zip_stat_t stat;
    zip_stat_init(&stat);
    if (zip_stat_index(archive, index, 0, &stat) != 0) {
        throw ZipError("无法获取ZIP条目信息");
    }

    // 创建临时目录
    std::filesystem::path temp_dir = std::filesystem::temp_directory_path() / "digiguide_temp";
    std::filesystem::create_directories(temp_dir);

    // 生成临时文件路径
    std::string entry_name = stat.name;
    std::filesystem::path temp_file = temp_dir / entry_name;

    // 确保父目录存在
    if (temp_file.has_parent_path()) {
        std::filesystem::create_directories(temp_file.parent_path());
    }

    // 解压文件
    zip_file_t* file = zip_fopen_index(archive, index, 0);
    if (!file) {
        throw ZipError("无法打开ZIP条目");
    }

    std::ofstream output(temp_file, std::ios::binary);
    if (!output.is_open()) {
        zip_fclose(file);
        throw ZipError("无法创建临时文件");
    }

    std::vector<char> buffer(8192);
    zip_int64_t remaining = stat.size;
    while (remaining > 0) {
        zip_int64_t to_read = std::min<zip_int64_t>(buffer.size(), remaining);
        zip_int64_t bytes_read = zip_fread(file, buffer.data(), to_read);
        if (bytes_read <= 0) break;
        output.write(buffer.data(), bytes_read);
        remaining -= bytes_read;
    }

    zip_fclose(file);
    output.close();

    return temp_file.string();
}

// 在ZIP中搜索bugreport文件
std::optional<std::string> search_bugreport_in_zip(zip_t* archive) {
    zip_int64_t num_entries = zip_get_num_entries(archive, 0);
    if (num_entries == 0) return std::nullopt;

    // 第一轮：查找嵌套ZIP（三星等品牌格式）
    for (zip_int64_t i = 0; i < num_entries; i++) {
        const char* name = zip_get_name(archive, i, 0);
        if (name && ends_with(name, ".zip")) {
            try {
                std::string inner_path = extract_entry_to_temp(archive, i);
                zip_t* inner = zip_open(inner_path.c_str(), ZIP_RDONLY, nullptr);
                if (inner) {
                    auto result = search_bugreport_in_zip(inner);
                    zip_close(inner);
                    // 清理临时文件
                    std::filesystem::remove_all(std::filesystem::path(inner_path).parent_path());
                    if (result.has_value()) {
                        return result;
                    }
                }
            } catch (const ZipError&) {
                // 继续尝试其他文件
            }
        }
    }

    // 第二轮：直接查找bugreport TXT
    for (zip_int64_t i = 0; i < num_entries; i++) {
        const char* name = zip_get_name(archive, i, 0);
        if (name && contains_ignore_case(name, "bugreport") && ends_with(name, ".txt")) {
            try {
                std::string content = read_entry_text(archive, i);
                return content;
            } catch (const ZipError&) {
                // 继续尝试其他文件
            }
        }
    }

    // 第三轮：降级查找任何.txt文件
    for (zip_int64_t i = 0; i < num_entries; i++) {
        const char* name = zip_get_name(archive, i, 0);
        if (name && ends_with(name, ".txt")) {
            try {
                std::string content = read_entry_text(archive, i);
                // 验证是否是有效的bugreport内容
                if (content.find("ro.product.brand") != std::string::npos ||
                    content.find("battery") != std::string::npos) {
                    return content;
                }
            } catch (const ZipError&) {
                // 继续尝试其他文件
            }
        }
    }

    return std::nullopt;
}

#endif // USE_LIBZIP

} // anonymous namespace

// ========== ZIP解析实现 ==========

ZipParseResult ZipParser::parseFromFile(const std::string& zip_path) {
    ZipParseResult result;
    result.success = false;
    result.total_files_extracted = 0;
    result.nested_zips_processed = 0;

#ifdef USE_LIBZIP
    // 使用libzip实现完整解析
    int error_code = 0;
    zip_t* archive = zip_open(zip_path.c_str(), ZIP_RDONLY, &error_code);
    if (!archive) {
        result.error_message = "无法打开ZIP文件: " + zip_path + " (错误码: " + std::to_string(error_code) + ")";
        return result;
    }

    zip_int64_t num_entries = zip_get_num_entries(archive, 0);
    if (num_entries == 0) {
        result.error_message = "ZIP文件为空";
        zip_close(archive);
        return result;
    }

    try {
        // 搜索bugreport文件（支持嵌套ZIP）
        auto bugreport_content = search_bugreport_in_zip(archive);

        if (bugreport_content.has_value()) {
            result.main_bugreport_content = bugreport_content.value();
            result.files.emplace_back("bugreport.txt", bugreport_content.value());
            result.total_files_extracted = 1;
            result.success = true;
        } else {
            result.error_message = "未找到有效的bugreport文件";
        }

        zip_close(archive);
    } catch (const ZipError& e) {
        result.error_message = e.what();
        zip_close(archive);
    }
#else
    // 简化实现（不使用libzip）
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
#endif

    return result;
}

ZipParseResult ZipParser::parseFromMemory(const std::vector<uint8_t>& data) {
    ZipParseResult result;
    result.success = false;
    result.total_files_extracted = 0;
    result.nested_zips_processed = 0;

#ifdef USE_LIBZIP
    // 使用libzip从内存解析
    zip_source_t* source = zip_source_buffer_create(data.data(), data.size(), 0, nullptr);
    if (!source) {
        result.error_message = "无法创建ZIP源";
        return result;
    }

    zip_t* archive = zip_open_from_source(source, ZIP_RDONLY, nullptr);
    if (!archive) {
        zip_source_free(source);
        result.error_message = "无法从内存打开ZIP";
        return result;
    }

    zip_int64_t num_entries = zip_get_num_entries(archive, 0);
    if (num_entries == 0) {
        result.error_message = "ZIP内容为空";
        zip_close(archive);
        return result;
    }

    try {
        auto bugreport_content = search_bugreport_in_zip(archive);

        if (bugreport_content.has_value()) {
            result.main_bugreport_content = bugreport_content.value();
            result.files.emplace_back("bugreport.txt", bugreport_content.value());
            result.total_files_extracted = 1;
            result.success = true;
        } else {
            result.error_message = "未找到有效的bugreport文件";
        }

        zip_close(archive);
    } catch (const ZipError& e) {
        result.error_message = e.what();
        zip_close(archive);
    }
#else
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
#endif

    return result;
}

ZipParseResult ZipParser::parseNested(const std::string& parent_content) {
    ZipParseResult result;
    result.success = false;

    // 检查是否包含嵌套ZIP（通过文件名或内容特征）
    if (parent_content.find(".zip") != std::string::npos) {
        result.nested_zips_processed = 0;
    }

    return result;
}

std::optional<std::string> ZipParser::findBugreportFile(
    const std::vector<std::pair<std::string, std::string>>& files) {

    // 查找主 bugreport 文件
    for (const auto& [name, content] : files) {
        // 检查文件名
        if (contains_ignore_case(name, "bugreport")) {
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

#ifdef USE_LIBZIP
    zip_t* archive = zip_open(path.c_str(), ZIP_RDONLY, nullptr);
    if (!archive) return false;

    zip_int64_t num_entries = zip_get_num_entries(archive, 0);
    for (zip_int64_t i = 0; i < num_entries; i++) {
        try {
            const char* name = zip_get_name(archive, i, 0);
            if (!name) continue;

            std::string content = read_entry_text(archive, i);
            output.emplace_back(name, content);
        } catch (const ZipError&) {
            // 跳过失败的条目
        }
    }

    zip_close(archive);
    return true;
#else
    return false;  // 简化实现返回失败
#endif
}

void ZipParser::processNestedZips(ZipParseResult& result) {
    // 处理嵌套ZIP已在search_bugreport_in_zip中实现
}

} // namespace digiguide::core