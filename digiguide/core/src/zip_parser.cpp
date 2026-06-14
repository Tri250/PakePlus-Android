#include "zip_parser.h"
#include <fstream>
#include <sstream>
#include <algorithm>
#include <cstring>
#include <zlib.h>

namespace digiguide::core {

// ZIP 文件格式常量
constexpr uint32_t LOCAL_FILE_HEADER_SIG = 0x04034b50;
constexpr uint32_t CENTRAL_DIR_HEADER_SIG = 0x02014b50;
constexpr uint32_t END_OF_CENTRAL_DIR_SIG = 0x06054b50;

// 解析 ZIP 文件头结构
struct ZipLocalFileHeader {
    uint32_t signature;
    uint16_t version_needed;
    uint16_t general_flags;
    uint16_t compression_method;
    uint16_t mod_time;
    uint16_t mod_date;
    uint32_t crc32;
    uint32_t compressed_size;
    uint32_t uncompressed_size;
    uint16_t filename_length;
    uint16_t extra_length;
};

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

    if (data.size() < 4) {
        result.error_message = "文件太小，不是有效的ZIP格式";
        return result;
    }

    // 检查 ZIP 文件签名
    uint32_t sig = *reinterpret_cast<const uint32_t*>(data.data());
    if (sig != LOCAL_FILE_HEADER_SIG) {
        // 不是标准 ZIP，尝试作为纯文本处理（兼容旧逻辑）
        std::string text_content(data.begin(), data.end());
        if (text_content.find("bugreport") != std::string::npos ||
            text_content.find("ro.product.brand") != std::string::npos ||
            text_content.find("healthd:") != std::string::npos ||
            text_content.find("fc=") != std::string::npos) {
            result.files.emplace_back("bugreport.txt", text_content);
            result.main_bugreport_content = text_content;
            result.total_files_extracted = 1;
            result.success = true;
            return result;
        }
        result.error_message = "不是有效的ZIP格式";
        return result;
    }

    // 解析 ZIP 文件
    size_t offset = 0;
    while (offset < data.size() - 30) {
        // 检查本地文件头签名
        if (offset + 4 > data.size()) break;
        
        uint32_t file_sig = *reinterpret_cast<const uint32_t*>(data.data() + offset);
        
        // 如果遇到中央目录头，停止解析
        if (file_sig == CENTRAL_DIR_HEADER_SIG || file_sig == END_OF_CENTRAL_DIR_SIG) {
            break;
        }
        
        if (file_sig != LOCAL_FILE_HEADER_SIG) {
            offset++;
            continue;
        }

        // 解析本地文件头
        if (offset + 30 > data.size()) break;
        
        ZipLocalFileHeader header;
        std::memcpy(&header, data.data() + offset, 30);

        // 读取文件名
        if (offset + 30 + header.filename_length > data.size()) break;
        
        std::string filename(reinterpret_cast<const char*>(data.data() + offset + 30),
                             header.filename_length);

        // 跳过目录条目
        if (filename.empty() || filename.back() == '/') {
            offset += 30 + header.filename_length + header.extra_length + header.compressed_size;
            continue;
        }

        // 定位文件数据
        size_t data_offset = offset + 30 + header.filename_length + header.extra_length;
        
        if (data_offset + header.compressed_size > data.size()) {
            result.error_message = "ZIP文件数据不完整";
            break;
        }

        // 提取文件内容
        std::string content;
        
        if (header.compression_method == 0) {
            // 未压缩
            content.assign(reinterpret_cast<const char*>(data.data() + data_offset),
                          header.uncompressed_size);
        } else if (header.compression_method == 8) {
            // Deflate 压缩
            content = decompressDeflate(data.data() + data_offset, 
                                        header.compressed_size,
                                        header.uncompressed_size);
            if (content.empty() && header.uncompressed_size > 0) {
                // 解压失败，尝试原始数据
                content.assign(reinterpret_cast<const char*>(data.data() + data_offset),
                              header.compressed_size);
            }
        } else {
            // 其他压缩方法，尝试原始数据
            content.assign(reinterpret_cast<const char*>(data.data() + data_offset),
                          header.compressed_size);
        }

        // 添加到结果
        result.files.emplace_back(filename, content);
        result.total_files_extracted++;

        // 检查是否是主 bugreport 文件
        if (filename.find("bugreport") != std::string::npos && 
            filename.find(".txt") != std::string::npos) {
            result.main_bugreport_content = content;
        }

        // 检查嵌套 ZIP
        if (filename.find(".zip") != std::string::npos) {
            std::vector<uint8_t> nested_data(content.begin(), content.end());
            ZipParseResult nested_result = parseFromMemory(nested_data);
            if (nested_result.success) {
                result.nested_results.push_back(nested_result);
                result.nested_zips_processed++;
                
                // 合并嵌套 ZIP 的文件
                for (const auto& nested_file : nested_result.files) {
                    result.files.emplace_back(filename + "/" + nested_file.first, nested_file.second);
                }
                
                // 如果嵌套 ZIP 包含 bugreport，也设置为主内容
                if (nested_result.main_bugreport_content.has_value() && 
                    !result.main_bugreport_content.has_value()) {
                    result.main_bugreport_content = nested_result.main_bugreport_content;
                }
            }
        }

        // 移动到下一个文件
        offset = data_offset + header.compressed_size;
    }

    // 如果没有找到主 bugreport 文件，查找其他可能的目标文件
    if (!result.main_bugreport_content.has_value()) {
        for (const auto& [name, content] : result.files) {
            // 检查文件名
            if (name.find("bugreport") != std::string::npos ||
                name.find("dumpstate") != std::string::npos ||
                name == "FS/data/log/power.log" ||
                name.find("battery") != std::string::npos) {
                result.main_bugreport_content = content;
                break;
            }
            
            // 检查内容特征
            if (content.find("ro.product.brand") != std::string::npos ||
                content.find("healthd:") != std::string::npos ||
                content.find("fc=") != std::string::npos ||
                content.find("Min learned battery capacity") != std::string::npos) {
                result.main_bugreport_content = content;
                break;
            }
        }
    }

    // 如果仍然没有找到，合并所有文本文件
    if (!result.main_bugreport_content.has_value() && !result.files.empty()) {
        std::ostringstream combined;
        for (const auto& [name, content] : result.files) {
            if (name.find(".txt") != std::string::npos || 
                content.find("healthd:") != std::string::npos ||
                content.find("battery") != std::string::npos) {
                combined << "=== " << name << " ===\n";
                combined << content << "\n\n";
            }
        }
        std::string combined_content = combined.str();
        if (!combined_content.empty()) {
            result.main_bugreport_content = combined_content;
        }
    }

    result.success = result.total_files_extracted > 0;
    
    if (!result.success) {
        result.error_message = "ZIP文件解析失败，未找到有效内容";
    }

    return result;
}

std::string ZipParser::decompressDeflate(const uint8_t* compressed_data, 
                                          uint32_t compressed_size,
                                          uint32_t uncompressed_size) {
    if (compressed_size == 0 || uncompressed_size == 0) {
        return "";
    }

    std::string result(uncompressed_size, '\0');
    
    z_stream strm;
    strm.zalloc = Z_NULL;
    strm.zfree = Z_NULL;
    strm.opaque = Z_NULL;
    strm.avail_in = compressed_size;
    strm.next_in = const_cast<uint8_t*>(compressed_data);
    strm.avail_out = uncompressed_size;
    strm.next_out = reinterpret_cast<uint8_t*>(result.data());

    // 使用 raw deflate（-MAX_WBITS 表示无 zlib/gzip 头）
    int ret = inflateInit2(&strm, -MAX_WBITS);
    if (ret != Z_OK) {
        return "";
    }

    ret = inflate(&strm, Z_FINISH);
    if (ret != Z_STREAM_END && ret != Z_OK) {
        inflateEnd(&strm);
        return "";
    }

    result.resize(strm.total_out);
    inflateEnd(&strm);

    return result;
}

ZipParseResult ZipParser::parseNested(const std::string& parent_content) {
    std::vector<uint8_t> data(parent_content.begin(), parent_content.end());
    return parseFromMemory(data);
}

std::optional<std::string> ZipParser::findBugreportFile(
    const std::vector<std::pair<std::string, std::string>>& files) {

    // 查找主 bugreport 文件（按优先级）
    std::vector<std::string> priority_patterns = {
        "bugreport", "dumpstate_board", "dumpstate", "battery", "power.log"
    };

    for (const auto& pattern : priority_patterns) {
        for (const auto& [name, content] : files) {
            if (name.find(pattern) != std::string::npos) {
                return content;
            }
        }
    }

    // 检查内容特征
    for (const auto& [name, content] : files) {
        if (content.find("ro.product.brand") != std::string::npos ||
            content.find("healthd:") != std::string::npos ||
            content.find("fc=") != std::string::npos ||
            content.find("Min learned battery capacity") != std::string::npos) {
            return content;
        }
    }

    return std::nullopt;
}

bool ZipParser::extractZip(const std::string& path,
                           std::vector<std::pair<std::string, std::string>>& output) {
    ZipParseResult result = parseFromFile(path);
    if (!result.success) {
        return false;
    }
    
    output = result.files;
    return true;
}

void ZipParser::processNestedZips(ZipParseResult& result) {
    // 处理嵌套 ZIP
    for (auto& [name, content] : result.files) {
        if (name.find(".zip") != std::string::npos) {
            std::vector<uint8_t> nested_data(content.begin(), content.end());
            ZipParseResult nested_result = parseFromMemory(nested_data);
            if (nested_result.success) {
                result.nested_results.push_back(nested_result);
                result.nested_zips_processed++;
            }
        }
    }
}

} // namespace digiguide::core