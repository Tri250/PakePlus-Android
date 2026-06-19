#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import json
from collections import Counter
from pathlib import Path

DB_PATH = Path("/workspace/BatteryHealthApp/app/src/main/assets/device_database.json")

NEW_DEVICES = [
    {
        "brand": "xiaomi",
        "model": "Xiaomi 15S Pro",
        "codename": "dijun",
        "market_name": "小米 15S Pro",
        "release_date": "2025-05",
        "battery_mah": 6100,
        "typical_charge_w": 90,
        "wireless_charge_w": 50,
        "processor": "玄戒 O1",
        "ram_gb": 16,
        "storage_gb": 512,
        "screen": "6.73\" 2K"
    },
    {
        "brand": "oppo",
        "model": "OPPO Reno14",
        "codename": "PLA110",
        "market_name": "OPPO Reno14",
        "release_date": "2025-05",
        "battery_mah": 6000,
        "typical_charge_w": 80,
        "wireless_charge_w": 0,
        "processor": "天玑 8350",
        "ram_gb": 12,
        "storage_gb": 256,
        "screen": "6.59\" 1.5K"
    },
    {
        "brand": "oppo",
        "model": "OPPO Reno14 Pro",
        "codename": "PKZ110",
        "market_name": "OPPO Reno14 Pro",
        "release_date": "2025-05",
        "battery_mah": 6200,
        "typical_charge_w": 80,
        "wireless_charge_w": 50,
        "processor": "天玑 8450",
        "ram_gb": 12,
        "storage_gb": 256,
        "screen": "6.83\" 1.5K"
    },
    {
        "brand": "vivo",
        "model": "vivo X200s",
        "codename": "V2458A",
        "market_name": "vivo X200s",
        "release_date": "2025-04",
        "battery_mah": 6200,
        "typical_charge_w": 90,
        "wireless_charge_w": 40,
        "processor": "天玑 9400+",
        "ram_gb": 12,
        "storage_gb": 256,
        "screen": "6.67\" 1.5K"
    },
    {
        "brand": "vivo",
        "model": "vivo S30",
        "codename": "V2464A",
        "market_name": "vivo S30",
        "release_date": "2025-05",
        "battery_mah": 6500,
        "typical_charge_w": 90,
        "wireless_charge_w": 0,
        "processor": "骁龙 7 Gen 4",
        "ram_gb": 12,
        "storage_gb": 256,
        "screen": "6.67\" 1.5K"
    },
    {
        "brand": "vivo",
        "model": "vivo S30 Pro mini",
        "codename": "V2465A",
        "market_name": "vivo S30 Pro mini",
        "release_date": "2025-05",
        "battery_mah": 6500,
        "typical_charge_w": 90,
        "wireless_charge_w": 0,
        "processor": "天玑 9300+",
        "ram_gb": 12,
        "storage_gb": 256,
        "screen": "6.31\" 1.5K"
    },
    {
        "brand": "honor",
        "model": "Honor 400",
        "codename": "DNN-AN00",
        "market_name": "荣耀 400",
        "release_date": "2025-05",
        "battery_mah": 7200,
        "typical_charge_w": 80,
        "wireless_charge_w": 0,
        "processor": "骁龙 7 Gen 4",
        "ram_gb": 12,
        "storage_gb": 256,
        "screen": "6.55\" 1.5K"
    },
    {
        "brand": "honor",
        "model": "Honor 400 Pro",
        "codename": "DNP-AN00",
        "market_name": "荣耀 400 Pro",
        "release_date": "2025-05",
        "battery_mah": 7200,
        "typical_charge_w": 90,
        "wireless_charge_w": 50,
        "processor": "骁龙 8 Gen 3",
        "ram_gb": 12,
        "storage_gb": 256,
        "screen": "6.55\" 1.5K"
    },
    {
        "brand": "iqoo",
        "model": "iQOO Neo10 Pro+",
        "codename": "V2463A",
        "market_name": "iQOO Neo10 Pro+",
        "release_date": "2025-05",
        "battery_mah": 6800,
        "typical_charge_w": 120,
        "wireless_charge_w": 0,
        "processor": "骁龙 8 Elite",
        "ram_gb": 12,
        "storage_gb": 256,
        "screen": "6.82\" 2K"
    },
    {
        "brand": "realme",
        "model": "realme GT7",
        "codename": "RMX5061",
        "market_name": "真我 GT7",
        "release_date": "2025-04",
        "battery_mah": 7200,
        "typical_charge_w": 100,
        "wireless_charge_w": 0,
        "processor": "天玑 9400+",
        "ram_gb": 12,
        "storage_gb": 256,
        "screen": "6.80\" 1.5K"
    },
    {
        "brand": "realme",
        "model": "realme Neo7x",
        "codename": "RMX5071",
        "market_name": "真我 Neo7x",
        "release_date": "2025-02",
        "battery_mah": 6000,
        "typical_charge_w": 45,
        "wireless_charge_w": 0,
        "processor": "骁龙 6 Gen 4",
        "ram_gb": 8,
        "storage_gb": 256,
        "screen": "6.67\" FHD+"
    },
    {
        "brand": "redmagic",
        "model": "REDMAGIC 10S Pro",
        "codename": "nx789j",
        "market_name": "红魔 10S Pro",
        "release_date": "2025-05",
        "battery_mah": 7050,
        "typical_charge_w": 80,
        "wireless_charge_w": 0,
        "processor": "骁龙 8 至尊领先版",
        "ram_gb": 12,
        "storage_gb": 256,
        "screen": "6.85\" 1.5K"
    },
    {
        "brand": "redmagic",
        "model": "REDMAGIC 10S Pro+",
        "codename": "nx789j",
        "market_name": "红魔 10S Pro+",
        "release_date": "2025-05",
        "battery_mah": 7500,
        "typical_charge_w": 120,
        "wireless_charge_w": 0,
        "processor": "骁龙 8 至尊领先版",
        "ram_gb": 16,
        "storage_gb": 512,
        "screen": "6.85\" 1.5K"
    },
    {
        "brand": "nubia",
        "model": "nubia Z80 Ultra",
        "codename": "nx741j",
        "market_name": "努比亚 Z80 Ultra",
        "release_date": "2025-10",
        "battery_mah": 7200,
        "typical_charge_w": 90,
        "wireless_charge_w": 80,
        "processor": "第五代骁龙 8 至尊版",
        "ram_gb": 12,
        "storage_gb": 512,
        "screen": "6.85\" 1.5K"
    }
]


def main():
    with DB_PATH.open("r", encoding="utf-8") as f:
        data = json.load(f)

    original_count = len(data["devices"])
    existing_models = {(d["brand"], d["model"]) for d in data["devices"]}

    added = 0
    skipped = []
    for dev in NEW_DEVICES:
        key = (dev["brand"], dev["model"])
        if key in existing_models:
            skipped.append(dev["model"])
            continue
        data["devices"].append(dev)
        existing_models.add(key)
        added += 1

    data["description"] = "2024-2026年6月18日国内品牌手机官网在售机型电池与配置数据库"
    # version 保持 "2026.06.18"

    with DB_PATH.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")

    total_count = len(data["devices"])
    brand_counts = Counter(d["brand"] for d in data["devices"])
    latest_release = max(d["release_date"] for d in data["devices"])

    print(f"原始条目数: {original_count}")
    print(f"新增条目数: {added}")
    if skipped:
        print(f"跳过（已存在）: {', '.join(skipped)}")
    print(f"总条目数: {total_count}")
    print(f"最晚 release_date: {latest_release}")
    print("各品牌条目数:")
    for brand, count in sorted(brand_counts.items()):
        print(f"  {brand}: {count}")


if __name__ == "__main__":
    main()
