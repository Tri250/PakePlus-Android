/**
 * 全国真实 POI 数据集(公开建筑/小区/商场名 + 经纬度)
 * 覆盖全国 30+ 主要城市,用于离线演示周边客群
 */
export interface RealPOI {
  name: string;
  city: string;
  category: 'office' | 'mall' | 'school' | 'residence' | 'subway' | 'park';
  lng: number;
  lat: number;
  scale: number;
}

// ============ 全国各城市 POI 数据 ============
export const NATIONAL_POI: RealPOI[] = [
  // ============ 北京 ============
  // 国贸 CBD
  { name: '国贸大厦', city: '北京', category: 'office', lng: 116.4586, lat: 39.9085, scale: 4500 },
  { name: '中国尊', city: '北京', category: 'office', lng: 116.4606, lat: 39.9117, scale: 12000 },
  { name: '央视新址', city: '北京', category: 'office', lng: 116.4655, lat: 39.9113, scale: 8000 },
  { name: '建外 SOHO', city: '北京', category: 'office', lng: 116.4541, lat: 39.9088, scale: 3500 },
  { name: '三里屯太古里', city: '北京', category: 'mall', lng: 116.4533, lat: 39.9356, scale: 35000 },
  { name: '望京 SOHO', city: '北京', category: 'office', lng: 116.4796, lat: 39.9967, scale: 5500 },
  { name: '百度科技园', city: '北京', category: 'office', lng: 116.3050, lat: 39.9890, scale: 8500 },
  { name: '腾讯北京总部', city: '北京', category: 'office', lng: 116.3070, lat: 39.9850, scale: 6500 },
  { name: '朝阳大悦城', city: '北京', category: 'mall', lng: 116.4980, lat: 39.9210, scale: 65000 },
  { name: '国贸站', city: '北京', category: 'subway', lng: 116.4600, lat: 39.9090, scale: 28000 },
  { name: '朝阳公园', city: '北京', category: 'park', lng: 116.4800, lat: 39.9390, scale: 8000 },
  { name: '富力城', city: '北京', category: 'residence', lng: 116.4600, lat: 39.8910, scale: 5500 },
  { name: '人大附中', city: '北京', category: 'school', lng: 116.4900, lat: 39.9150, scale: 1800 },

  // ============ 上海 ============
  { name: '上海中心大厦', city: '上海', category: 'office', lng: 121.5063, lat: 31.2304, scale: 18000 },
  { name: '环球金融中心', city: '上海', category: 'office', lng: 121.5058, lat: 31.2308, scale: 12000 },
  { name: '金茂大厦', city: '上海', category: 'office', lng: 121.5049, lat: 31.2317, scale: 8000 },
  { name: '陆家嘴 SOHO', city: '上海', category: 'office', lng: 121.5130, lat: 31.2350, scale: 5000 },
  { name: '上海国金中心', city: '上海', category: 'mall', lng: 121.5072, lat: 31.2325, scale: 50000 },
  { name: '正大广场', city: '上海', category: 'mall', lng: 121.5030, lat: 31.2330, scale: 45000 },
  { name: '南京西路恒隆', city: '上海', category: 'mall', lng: 121.4680, lat: 31.2380, scale: 35000 },
  { name: '浦东嘉里城', city: '上海', category: 'mall', lng: 121.5500, lat: 31.2280, scale: 40000 },
  { name: '陆家嘴站', city: '上海', category: 'subway', lng: 121.5050, lat: 31.2320, scale: 35000 },
  { name: '世纪公园', city: '上海', category: 'park', lng: 121.5450, lat: 31.2160, scale: 12000 },
  { name: '仁恒滨江园', city: '上海', category: 'residence', lng: 121.5200, lat: 31.2300, scale: 4500 },
  { name: '上海中学', city: '上海', category: 'school', lng: 121.4450, lat: 31.1680, scale: 2500 },
  { name: '张江高科技园区', city: '上海', category: 'office', lng: 121.5900, lat: 31.2200, scale: 25000 },

  // ============ 广州 ============
  { name: '广州塔', city: '广州', category: 'office', lng: 113.3245, lat: 23.1047, scale: 3500 },
  { name: '珠江新城西塔', city: '广州', category: 'office', lng: 113.3265, lat: 23.1238, scale: 10000 },
  { name: '珠江新城东塔', city: '广州', category: 'office', lng: 113.3310, lat: 23.1220, scale: 12000 },
  { name: '太古汇', city: '广州', category: 'mall', lng: 113.3250, lat: 23.1200, scale: 40000 },
  { name: '天环广场', city: '广州', category: 'mall', lng: 113.3210, lat: 23.1180, scale: 35000 },
  { name: '正佳广场', city: '广州', category: 'mall', lng: 113.3200, lat: 23.1250, scale: 60000 },
  { name: '珠江新城站', city: '广州', category: 'subway', lng: 113.3260, lat: 23.1210, scale: 28000 },
  { name: '广州图书馆', city: '广州', category: 'office', lng: 113.3270, lat: 23.1180, scale: 2000 },
  { name: '猎德花园', city: '广州', category: 'residence', lng: 113.3350, lat: 23.1250, scale: 6000 },
  { name: '华南理工大学', city: '广州', category: 'school', lng: 113.3660, lat: 23.1460, scale: 28000 },

  // ============ 深圳 ============
  { name: '平安金融中心', city: '深圳', category: 'office', lng: 114.0583, lat: 22.5431, scale: 15000 },
  { name: '华润大厦', city: '深圳', category: 'office', lng: 114.0430, lat: 22.5380, scale: 6000 },
  { name: '腾讯滨海大厦', city: '深圳', category: 'office', lng: 113.9180, lat: 22.4850, scale: 8000 },
  { name: '深圳湾科技园', city: '深圳', category: 'office', lng: 113.9200, lat: 22.4800, scale: 35000 },
  { name: '万象城', city: '深圳', category: 'mall', lng: 114.0550, lat: 22.5450, scale: 55000 },
  { name: '海岸城', city: '深圳', category: 'mall', lng: 113.9150, lat: 22.4880, scale: 45000 },
  { name: '福田站', city: '深圳', category: 'subway', lng: 114.0560, lat: 22.5420, scale: 32000 },
  { name: '市民中心', city: '深圳', category: 'office', lng: 114.0590, lat: 22.5400, scale: 4000 },
  { name: '香蜜湖一号', city: '深圳', category: 'residence', lng: 114.0350, lat: 22.5480, scale: 3500 },
  { name: '深圳中学', city: '深圳', category: 'school', lng: 114.0750, lat: 22.5550, scale: 2000 },

  // ============ 杭州 ============
  { name: '杭州国际会议中心', city: '杭州', category: 'office', lng: 120.1560, lat: 30.2750, scale: 4500 },
  { name: '浙江财富金融中心', city: '杭州', category: 'office', lng: 120.1530, lat: 30.2720, scale: 5500 },
  { name: '来福士广场', city: '杭州', category: 'mall', lng: 120.1520, lat: 30.2710, scale: 42000 },
  { name: '湖滨银泰', city: '杭州', category: 'mall', lng: 120.1500, lat: 30.2700, scale: 50000 },
  { name: '杭州东站', city: '杭州', category: 'subway', lng: 120.1850, lat: 30.2830, scale: 45000 },
  { name: '阿里巴巴总部', city: '杭州', category: 'office', lng: 120.0950, lat: 30.2400, scale: 25000 },
  { name: '西湖文化广场', city: '杭州', category: 'mall', lng: 120.1650, lat: 30.2900, scale: 30000 },
  { name: '绿城春江花月', city: '杭州', category: 'residence', lng: 120.1450, lat: 30.2550, scale: 4200 },
  { name: '杭州二中', city: '杭州', category: 'school', lng: 120.1900, lat: 30.2000, scale: 2200 },
  { name: '西湖', city: '杭州', category: 'park', lng: 120.1500, lat: 30.2740, scale: 50000 },

  // ============ 成都 ============
  { name: '天府国际金融中心', city: '成都', category: 'office', lng: 104.0680, lat: 30.5780, scale: 8000 },
  { name: '成都银泰中心', city: '成都', category: 'office', lng: 104.0650, lat: 30.5750, scale: 6000 },
  { name: '太古里', city: '成都', category: 'mall', lng: 104.0680, lat: 30.6580, scale: 48000 },
  { name: 'IFS 国际金融中心', city: '成都', category: 'mall', lng: 104.0690, lat: 30.6570, scale: 55000 },
  { name: '春熙路站', city: '成都', category: 'subway', lng: 104.0670, lat: 30.6560, scale: 38000 },
  { name: '环球中心', city: '成都', category: 'mall', lng: 104.0500, lat: 30.5800, scale: 80000 },
  { name: '软件园', city: '成都', category: 'office', lng: 104.0050, lat: 30.5780, scale: 28000 },
  { name: '麓湖生态城', city: '成都', category: 'residence', lng: 104.0300, lat: 30.5000, scale: 8000 },
  { name: '成都七中', city: '成都', category: 'school', lng: 104.0750, lat: 30.6650, scale: 3000 },
  { name: '人民公园', city: '成都', category: 'park', lng: 104.0600, lat: 30.6600, scale: 6000 },

  // ============ 武汉 ============
  { name: '武汉中心大厦', city: '武汉', category: 'office', lng: 114.2830, lat: 30.5860, scale: 6500 },
  { name: '绿地中心', city: '武汉', category: 'office', lng: 114.3100, lat: 30.5800, scale: 10000 },
  { name: '楚河汉街', city: '武汉', category: 'mall', lng: 114.3150, lat: 30.5750, scale: 55000 },
  { name: '武商广场', city: '武汉', category: 'mall', lng: 114.2650, lat: 30.5780, scale: 45000 },
  { name: '光谷广场', city: '武汉', category: 'subway', lng: 114.4000, lat: 30.5200, scale: 42000 },
  { name: '光谷软件园', city: '武汉', category: 'office', lng: 114.4100, lat: 30.5100, scale: 22000 },
  { name: '武汉大学', city: '武汉', category: 'school', lng: 114.3500, lat: 30.5400, scale: 32000 },
  { name: '东湖', city: '武汉', category: 'park', lng: 114.3800, lat: 30.5500, scale: 25000 },

  // ============ 西安 ============
  { name: '绿地中心', city: '西安', category: 'office', lng: 108.9500, lat: 34.2100, scale: 7000 },
  { name: '赛格国际', city: '西安', category: 'mall', lng: 108.9550, lat: 34.2280, scale: 60000 },
  { name: '回民街', city: '西安', category: 'mall', lng: 108.9470, lat: 34.2700, scale: 40000 },
  { name: '大雁塔', city: '西安', category: 'park', lng: 108.9520, lat: 34.2250, scale: 20000 },
  { name: '高新区', city: '西安', category: 'office', lng: 108.9200, lat: 34.2000, scale: 35000 },
  { name: '西安交大', city: '西安', category: 'school', lng: 108.9530, lat: 34.2200, scale: 25000 },

  // ============ 重庆 ============
  { name: '解放碑', city: '重庆', category: 'office', lng: 106.5880, lat: 29.5630, scale: 5500 },
  { name: '江北嘴金融中心', city: '重庆', category: 'office', lng: 106.5900, lat: 29.5800, scale: 8000 },
  { name: '龙湖时代天街', city: '重庆', category: 'mall', lng: 106.5400, lat: 29.5200, scale: 70000 },
  { name: '洪崖洞', city: '重庆', category: 'mall', lng: 106.5850, lat: 29.5600, scale: 35000 },
  { name: '重庆大学', city: '重庆', category: 'school', lng: 106.4900, lat: 29.4900, scale: 28000 },

  // ============ 天津 ============
  { name: '天津环球金融中心', city: '天津', category: 'office', lng: 117.2030, lat: 39.1310, scale: 6000 },
  { name: '津湾广场', city: '天津', category: 'mall', lng: 117.2000, lat: 39.1330, scale: 40000 },
  { name: '天津站', city: '天津', category: 'subway', lng: 117.2050, lat: 39.1280, scale: 35000 },
  { name: '南开大学', city: '天津', category: 'school', lng: 117.2200, lat: 39.1080, scale: 22000 },

  // ============ 南京 ============
  { name: '紫峰大厦', city: '南京', category: 'office', lng: 118.7900, lat: 32.0620, scale: 7000 },
  { name: '新街口德基', city: '南京', category: 'mall', lng: 118.7900, lat: 32.0600, scale: 55000 },
  { name: '南京南站', city: '南京', category: 'subway', lng: 118.7850, lat: 31.9550, scale: 48000 },
  { name: '南京大学', city: '南京', category: 'school', lng: 118.7950, lat: 32.0650, scale: 25000 },

  // ============ 苏州 ============
  { name: '苏州中心', city: '苏州', category: 'mall', lng: 120.6350, lat: 31.3100, scale: 65000 },
  { name: '东方之门', city: '苏州', category: 'office', lng: 120.6300, lat: 31.3080, scale: 4500 },
  { name: '工业园区', city: '苏州', category: 'office', lng: 120.6400, lat: 31.3200, scale: 35000 },
  { name: '苏州大学', city: '苏州', category: 'school', lng: 120.6200, lat: 31.3250, scale: 20000 },

  // ============ 无锡 ============
  { name: '恒隆广场', city: '无锡', category: 'mall', lng: 120.2950, lat: 31.5400, scale: 38000 },
  { name: '无锡东站', city: '无锡', category: 'subway', lng: 120.4800, lat: 31.5800, scale: 28000 },

  // ============ 宁波 ============
  { name: '宁波中心', city: '宁波', category: 'office', lng: 121.5550, lat: 29.8700, scale: 5500 },
  { name: '天一广场', city: '宁波', category: 'mall', lng: 121.5500, lat: 29.8750, scale: 45000 },

  // ============ 长沙 ============
  { name: '国金中心', city: '长沙', category: 'office', lng: 112.9400, lat: 28.2300, scale: 6500 },
  { name: 'IFS', city: '长沙', category: 'mall', lng: 112.9420, lat: 28.2280, scale: 52000 },
  { name: '橘子洲', city: '长沙', category: 'park', lng: 112.9350, lat: 28.2200, scale: 18000 },
  { name: '湖南大学', city: '长沙', category: 'school', lng: 112.9200, lat: 28.1950, scale: 30000 },

  // ============ 郑州 ============
  { name: '绿地中心', city: '郑州', category: 'office', lng: 113.6500, lat: 34.7550, scale: 5000 },
  { name: '丹尼斯大卫城', city: '郑州', category: 'mall', lng: 113.6450, lat: 34.7580, scale: 50000 },
  { name: '郑州东站', city: '郑州', category: 'subway', lng: 113.7450, lat: 34.7500, scale: 45000 },

  // ============ 青岛 ============
  { name: '青岛中心', city: '青岛', category: 'office', lng: 120.3350, lat: 36.0680, scale: 4500 },
  { name: '万象城', city: '青岛', category: 'mall', lng: 120.3380, lat: 36.0650, scale: 48000 },
  { name: '五四广场', city: '青岛', category: 'park', lng: 120.3370, lat: 36.0630, scale: 12000 },

  // ============ 厦门 ============
  { name: '世茂海峡大厦', city: '厦门', category: 'office', lng: 118.0880, lat: 24.4750, scale: 5000 },
  { name: 'SM 城市广场', city: '厦门', category: 'mall', lng: 118.0700, lat: 24.5100, scale: 42000 },
  { name: '厦门大学', city: '厦门', category: 'school', lng: 118.0900, lat: 24.4700, scale: 25000 },

  // ============ 合肥 ============
  { name: '安徽广电中心', city: '合肥', category: 'office', lng: 117.2200, lat: 31.8250, scale: 4000 },
  { name: '华润万象城', city: '合肥', category: 'mall', lng: 117.2100, lat: 31.8300, scale: 45000 },

  // ============ 济南 ============
  { name: '绿地中心', city: '济南', category: 'office', lng: 117.0050, lat: 36.6650, scale: 5500 },
  { name: '恒隆广场', city: '济南', category: 'mall', lng: 117.0000, lat: 36.6680, scale: 38000 },

  // ============ 大连 ============
  { name: '国际金融中心', city: '大连', category: 'office', lng: 121.6200, lat: 38.9200, scale: 6000 },
  { name: '恒隆广场', city: '大连', category: 'mall', lng: 121.6150, lat: 38.9250, scale: 42000 },

  // ============ 沈阳 ============
  { name: '市府恒隆', city: '沈阳', category: 'office', lng: 123.4350, lat: 41.8050, scale: 5000 },
  { name: '万象城', city: '沈阳', category: 'mall', lng: 123.4380, lat: 41.7950, scale: 55000 },

  // ============ 哈尔滨 ============
  { name: '龙塔', city: '哈尔滨', category: 'office', lng: 126.6400, lat: 45.8000, scale: 3500 },
  { name: '中央大街', city: '哈尔滨', category: 'mall', lng: 126.6150, lat: 45.7980, scale: 45000 },

  // ============ 石家庄 ============
  { name: '华润万象城', city: '石家庄', category: 'mall', lng: 114.4900, lat: 38.0450, scale: 42000 },
  { name: '勒泰中心', city: '石家庄', category: 'mall', lng: 114.4950, lat: 38.0480, scale: 38000 },

  // ============ 太原 ============
  { name: '茂业天地', city: '太原', category: 'mall', lng: 112.5450, lat: 37.8750, scale: 40000 },
  { name: '万达广场', city: '太原', category: 'mall', lng: 112.5300, lat: 37.8700, scale: 45000 },

  // ============ 南昌 ============
  { name: '绿地中心', city: '南昌', category: 'office', lng: 115.8950, lat: 28.6850, scale: 5500 },
  { name: '红谷滩万达', city: '南昌', category: 'mall', lng: 115.8850, lat: 28.6800, scale: 48000 },

  // ============ 福州 ============
  { name: '世欧广场', city: '福州', category: 'mall', lng: 119.3100, lat: 26.0650, scale: 42000 },
  { name: '华润万象城', city: '福州', category: 'mall', lng: 119.2850, lat: 26.0750, scale: 45000 },

  // ============ 昆明 ============
  { name: '恒隆广场', city: '昆明', category: 'mall', lng: 102.7200, lat: 25.0450, scale: 48000 },
  { name: '同德广场', city: '昆明', category: 'mall', lng: 102.7250, lat: 25.0550, scale: 42000 },

  // ============ 贵阳 ============
  { name: '亨特城市广场', city: '贵阳', category: 'mall', lng: 106.7100, lat: 26.5850, scale: 38000 },
  { name: '万达广场', city: '贵阳', category: 'mall', lng: 106.6950, lat: 26.5700, scale: 45000 },

  // ============ 南宁 ============
  { name: '华润万象城', city: '南宁', category: 'mall', lng: 108.3550, lat: 22.8200, scale: 55000 },
  { name: '航洋城', city: '南宁', category: 'mall', lng: 108.3580, lat: 22.8180, scale: 42000 },
];

// 按城市分组
export const POI_BY_CITY = NATIONAL_POI.reduce((acc, poi) => {
  if (!acc[poi.city]) acc[poi.city] = [];
  acc[poi.city].push(poi);
  return acc;
}, {} as Record<string, RealPOI[]>);

// 获取所有城市列表
export const CITIES = Object.keys(POI_BY_CITY);

// 按"规模"映射到客户密度(用于估算可触达客户数)
// 写字楼: 80% 工位为可触达白领;住宅: 户数 * 2.6 人/户;商场: 5% 日客流可复访
export const estimateAudience = (p: RealPOI): number => {
  switch (p.category) {
    case 'office':
      return Math.round(p.scale * 0.8);
    case 'residence':
      return Math.round(p.scale * 2.6);
    case 'mall':
      return Math.round(p.scale * 0.05);
    case 'school':
      return Math.round(p.scale * 0.6);
    case 'subway':
      return Math.round(p.scale * 0.15);
    case 'park':
      return Math.round(p.scale * 0.04);
    default:
      return p.scale;
  }
};
