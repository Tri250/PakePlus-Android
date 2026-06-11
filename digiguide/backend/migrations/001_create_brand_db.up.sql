-- 创建电池报告表
CREATE TABLE IF NOT EXISTS battery_reports (
    id SERIAL PRIMARY KEY,
    brand VARCHAR(50),
    model VARCHAR(100),
    sn VARCHAR(100),
    design_capacity_mah INTEGER,
    current_capacity_mah INTEGER,
    cycle_count INTEGER,
    manufacturing_date VARCHAR(50),
    temperature_celsius DECIMAL(10, 2),
    health_percentage DECIMAL(10, 2) NOT NULL,
    grade VARCHAR(5) NOT NULL,
    capacity_retention DECIMAL(10, 4),
    cycle_decay DECIMAL(10, 4),
    diagnosis_text TEXT,
    suggestions TEXT,
    report_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    raw_bugreport_path VARCHAR(255)
);

-- 创建SN查询历史表
CREATE TABLE IF NOT EXISTS query_history (
    id SERIAL PRIMARY KEY,
    sn VARCHAR(100) NOT NULL,
    brand VARCHAR(50) NOT NULL,
    factory_year INTEGER,
    factory_month INTEGER,
    factory_week INTEGER,
    half_year VARCHAR(20),
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    query_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    device_model VARCHAR(100)
);

-- 创建索引
CREATE INDEX idx_battery_reports_brand ON battery_reports(brand);
CREATE INDEX idx_battery_reports_health ON battery_reports(health_percentage);
CREATE INDEX idx_battery_reports_time ON battery_reports(report_time);
CREATE INDEX idx_query_history_sn ON query_history(sn);
CREATE INDEX idx_query_history_brand ON query_history(brand);
CREATE INDEX idx_query_history_time ON query_history(query_time);

-- 创建品牌SN规则表
CREATE TABLE IF NOT EXISTS brand_sn_rules (
    id SERIAL PRIMARY KEY,
    brand VARCHAR(50) NOT NULL UNIQUE,
    format_description TEXT NOT NULL,
    warranty_months INTEGER DEFAULT 12,
    official_api_url VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 插入品牌规则初始数据
INSERT INTO brand_sn_rules (brand, format_description, warranty_months) VALUES
('APPLE', 'Apple SN: 12位，第4位=半年代码，第5位=周次', 12),
('SAMSUNG', 'Samsung SN: 倒数第7位=年份，倒数第6位=月份', 12),
('HUAWEI', 'Huawei SN: 第6-7位=年份后两位，第8-9位=周次', 12),
('HONOR', 'Honor SN: 与华为类似，第6-7位=年份，第8-9位=周次', 12),
('XIAOMI', 'Xiaomi SN: 多种格式，IMEI或自定义编码', 12),
('OPPO', 'OPPO SN: 第4-5位含年份+月份编码', 12),
('VIVO', 'vivo SN: 第5-6位=年份，第7-8位=周次/月份', 12),
('LENOVO', 'Lenovo SN: ThinkPad格式，前4位=机型，第5位=年份', 12),
('HP', 'HP SN: 第3-4位=年份和地区，后续=周次', 12),
('ASUS', 'ASUS SN: 第2位=年份代码，第3位=月份代码', 12),
('DELL', 'Dell SN: 服务标签5-7位，需官方API查询', 12),
('APPLE_MAC', 'Apple Mac SN: 与iPhone类似，12位格式', 12);