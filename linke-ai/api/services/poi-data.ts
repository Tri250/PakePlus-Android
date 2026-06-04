/**
 * 真实北京 POI 数据集(公开建筑/小区/商场名 + 经纬度)
 * 数据来源:基于公开地图坐标整理,用于离线演示周边客群
 */
export interface RealPOI {
  name: string;
  category: 'office' | 'mall' | 'school' | 'residence' | 'subway' | 'park';
  lng: number;
  lat: number;
  // 估算规模(写字楼:工位数;住宅:户数;商场:日均客流)
  scale: number;
}

// 朝阳 CBD / 国贸 / 三里屯 / 望京 / 中关村 真实建筑
export const BEIJING_POI: RealPOI[] = [
  // ============ 写字楼 (office) ============
  // 国贸 CBD
  { name: '国贸大厦', category: 'office', lng: 116.4586, lat: 39.9085, scale: 4500 },
  { name: '国贸三期 A 阶段', category: 'office', lng: 116.4611, lat: 39.9090, scale: 6500 },
  { name: '国贸三期 B 阶段', category: 'office', lng: 116.4621, lat: 39.9105, scale: 5500 },
  { name: '中国尊 (中信大厦)', category: 'office', lng: 116.4606, lat: 39.9117, scale: 12000 },
  { name: '央视新址主楼', category: 'office', lng: 116.4655, lat: 39.9113, scale: 8000 },
  { name: '建外 SOHO 西区', category: 'office', lng: 116.4541, lat: 39.9088, scale: 3500 },
  { name: '建外 SOHO 东区', category: 'office', lng: 116.4571, lat: 39.9086, scale: 3800 },
  { name: '银泰中心', category: 'office', lng: 116.4570, lat: 39.9123, scale: 4200 },
  { name: '华贸中心 1 座', category: 'office', lng: 116.4560, lat: 39.9181, scale: 3800 },
  { name: '华贸中心 2 座', category: 'office', lng: 116.4570, lat: 39.9175, scale: 3600 },
  { name: '华贸中心 3 座', category: 'office', lng: 116.4555, lat: 39.9171, scale: 3000 },
  { name: '华贸写字楼', category: 'office', lng: 116.4565, lat: 39.9168, scale: 2800 },
  { name: '北京 SKP', category: 'office', lng: 116.4630, lat: 39.9142, scale: 1500 },
  { name: '嘉里中心', category: 'office', lng: 116.4542, lat: 39.9120, scale: 3200 },
  { name: '国际贸易中心 A 座', category: 'office', lng: 116.4593, lat: 39.9093, scale: 4000 },
  { name: '国际贸易中心 B 座', category: 'office', lng: 116.4601, lat: 39.9095, scale: 3800 },

  // 三里屯 / 工人体育场
  { name: '三里屯太古里北区', category: 'mall', lng: 116.4533, lat: 39.9356, scale: 35000 },
  { name: '三里屯 SOHO', category: 'office', lng: 116.4536, lat: 39.9380, scale: 8500 },
  { name: '工体北路 1 号', category: 'office', lng: 116.4547, lat: 39.9395, scale: 2200 },
  { name: '通盈中心', category: 'office', lng: 116.4562, lat: 39.9352, scale: 3000 },
  { name: '那里花园', category: 'mall', lng: 116.4525, lat: 39.9368, scale: 12000 },
  { name: '三里屯 Village 南区', category: 'mall', lng: 116.4530, lat: 39.9342, scale: 28000 },

  // 望京
  { name: '望京 SOHO T1', category: 'office', lng: 116.4796, lat: 39.9967, scale: 5500 },
  { name: '望京 SOHO T2', category: 'office', lng: 116.4803, lat: 39.9963, scale: 5500 },
  { name: '望京 SOHO T3', category: 'office', lng: 116.4810, lat: 39.9960, scale: 4800 },
  { name: '绿地中心 (望京)', category: 'office', lng: 116.4825, lat: 39.9975, scale: 4200 },
  { name: '浦项中心', category: 'office', lng: 116.4840, lat: 39.9985, scale: 3800 },
  { name: '利星行广场', category: 'office', lng: 116.4805, lat: 39.9990, scale: 3200 },
  { name: '北京 · 宝能中心', category: 'office', lng: 116.4770, lat: 39.9920, scale: 5000 },
  { name: '大西洋新城', category: 'residence', lng: 116.4750, lat: 39.9925, scale: 4200 },
  { name: '望京新城 A 区', category: 'residence', lng: 116.4760, lat: 39.9990, scale: 3800 },
  { name: '望京新城 B 区', category: 'residence', lng: 116.4772, lat: 40.0005, scale: 3200 },
  { name: '金茂府', category: 'residence', lng: 116.4720, lat: 39.9895, scale: 1800 },
  { name: '远洋万和城', category: 'residence', lng: 116.4745, lat: 39.9945, scale: 2400 },
  { name: '融科橄榄城', category: 'residence', lng: 116.4715, lat: 39.9960, scale: 3600 },
  { name: '澳洲康都', category: 'residence', lng: 116.4790, lat: 40.0020, scale: 2800 },

  // 朝阳公园 / 燕莎
  { name: '朝阳公园', category: 'park', lng: 116.4800, lat: 39.9390, scale: 8000 },
  { name: '团结湖公园', category: 'park', lng: 116.4610, lat: 39.9290, scale: 1500 },
  { name: '红领巾公园', category: 'park', lng: 116.4930, lat: 39.9290, scale: 2200 },
  { name: '朝阳大悦城', category: 'mall', lng: 116.4980, lat: 39.9210, scale: 65000 },
  { name: '蓝色港湾', category: 'mall', lng: 116.4810, lat: 39.9390, scale: 30000 },
  { name: '燕莎友谊商城', category: 'mall', lng: 116.4630, lat: 39.9480, scale: 18000 },
  { name: '亮马桥外交公寓', category: 'residence', lng: 116.4650, lat: 39.9480, scale: 1200 },
  { name: '京广中心', category: 'office', lng: 116.4650, lat: 39.9470, scale: 4500 },
  { name: '世纪财富中心', category: 'office', lng: 116.4635, lat: 39.9490, scale: 3500 },
  { name: '东方东路 19 号', category: 'office', lng: 116.4660, lat: 39.9465, scale: 2800 },

  // 双井 / 劲松
  { name: '富力广场', category: 'mall', lng: 116.4610, lat: 39.8920, scale: 45000 },
  { name: '合生汇', category: 'mall', lng: 116.4660, lat: 39.8950, scale: 55000 },
  { name: '双井 1 号', category: 'office', lng: 116.4620, lat: 39.8940, scale: 2200 },
  { name: '乐成中心', category: 'office', lng: 116.4650, lat: 39.8960, scale: 3000 },
  { name: '苹果社区北区', category: 'residence', lng: 116.4630, lat: 39.8910, scale: 4500 },
  { name: '苹果社区南区', category: 'residence', lng: 116.4640, lat: 39.8900, scale: 4200 },
  { name: '富力城 A 区', category: 'residence', lng: 116.4600, lat: 39.8910, scale: 5500 },
  { name: '富力城 B 区', category: 'residence', lng: 116.4610, lat: 39.8905, scale: 4800 },
  { name: '珠江帝景', category: 'residence', lng: 116.4670, lat: 39.8850, scale: 3800 },
  { name: 'CBD 总部公寓', category: 'residence', lng: 116.4650, lat: 39.9070, scale: 1800 },

  // 十里堡 / 朝阳北路
  { name: '十里堡北里', category: 'residence', lng: 116.4980, lat: 39.9210, scale: 4200 },
  { name: '十里堡南区', category: 'residence', lng: 116.4990, lat: 39.9180, scale: 3800 },
  { name: '京棉新城', category: 'residence', lng: 116.4940, lat: 39.9160, scale: 3600 },

  // 大望路 / 通惠河
  { name: '华贸购物中心', category: 'mall', lng: 116.4560, lat: 39.9150, scale: 35000 },
  { name: '万达广场 (CBD)', category: 'mall', lng: 116.4680, lat: 39.9080, scale: 40000 },
  { name: 'SOHO 现代城 A', category: 'office', lng: 116.4540, lat: 39.9110, scale: 3500 },
  { name: 'SOHO 现代城 B', category: 'office', lng: 116.4548, lat: 39.9112, scale: 3500 },
  { name: '远洋光华国际', category: 'office', lng: 116.4570, lat: 39.9140, scale: 4200 },

  // ============ 海淀 / 中关村 (覆盖 8-10 km 圈层) ============
  { name: '中关村大街 1 号', category: 'office', lng: 116.3100, lat: 39.9810, scale: 3200 },
  { name: '海淀剧院写字楼', category: 'office', lng: 116.3120, lat: 39.9820, scale: 1800 },
  { name: '理想国际大厦', category: 'office', lng: 116.3100, lat: 39.9840, scale: 4500 },
  { name: '新浪总部大厦', category: 'office', lng: 116.3120, lat: 39.9850, scale: 5500 },
  { name: '百度科技园 K1', category: 'office', lng: 116.3050, lat: 39.9890, scale: 8500 },
  { name: '百度科技园 K2', category: 'office', lng: 116.3060, lat: 39.9890, scale: 8500 },
  { name: '腾讯北京总部 (海淀)', category: 'office', lng: 116.3070, lat: 39.9850, scale: 6500 },
  { name: '中关村购物中心', category: 'mall', lng: 116.3100, lat: 39.9810, scale: 38000 },
  { name: '新中关购物中心', category: 'mall', lng: 116.3110, lat: 39.9830, scale: 28000 },
  { name: '北航家属区', category: 'residence', lng: 116.3450, lat: 39.9920, scale: 4500 },
  { name: '清华园住宅区', category: 'residence', lng: 116.3270, lat: 40.0030, scale: 5200 },
  { name: '北京大学家属区', category: 'residence', lng: 116.3160, lat: 39.9990, scale: 4800 },
  { name: '中关村南大街 5 号', category: 'residence', lng: 116.3200, lat: 39.9650, scale: 3200 },

  // ============ 学校 ============
  { name: '人大附中朝阳学校', category: 'school', lng: 116.4900, lat: 39.9150, scale: 1800 },
  { name: '陈经纶中学 (本部)', category: 'school', lng: 116.4720, lat: 39.9280, scale: 2200 },
  { name: '芳草地国际学校', category: 'school', lng: 116.4550, lat: 39.9280, scale: 1600 },
  { name: '八十中 (望京)', category: 'school', lng: 116.4760, lat: 40.0010, scale: 2000 },
  { name: '北京中学 (CBD)', category: 'school', lng: 116.4650, lat: 39.9120, scale: 1400 },
  { name: '中央美术学院', category: 'school', lng: 116.4630, lat: 39.9910, scale: 4500 },
  { name: '对外经贸大学', category: 'school', lng: 116.4280, lat: 39.9890, scale: 8500 },

  // ============ 地铁 ============
  { name: '国贸站 (1/10 号线)', category: 'subway', lng: 116.4600, lat: 39.9090, scale: 28000 },
  { name: '大望路站 (1/14 号线)', category: 'subway', lng: 116.4720, lat: 39.9080, scale: 18000 },
  { name: '四惠站 (1/八通线)', category: 'subway', lng: 116.4940, lat: 39.9100, scale: 22000 },
  { name: '四惠东站', category: 'subway', lng: 116.5020, lat: 39.9090, scale: 16000 },
  { name: '双井站 (10 号线)', category: 'subway', lng: 116.4620, lat: 39.8940, scale: 15000 },
  { name: '劲松站 (10 号线)', category: 'subway', lng: 116.4610, lat: 39.8850, scale: 12000 },
  { name: '团结湖站 (10 号线)', category: 'subway', lng: 116.4600, lat: 39.9370, scale: 9000 },
  { name: '农业展览馆站', category: 'subway', lng: 116.4620, lat: 39.9450, scale: 8000 },
  { name: '亮马桥站 (10 号线)', category: 'subway', lng: 116.4630, lat: 39.9490, scale: 10000 },
  { name: '望京站 (14/15 号线)', category: 'subway', lng: 116.4790, lat: 39.9970, scale: 16000 },
  { name: '望京东站', category: 'subway', lng: 116.4830, lat: 39.9970, scale: 12000 },
  { name: '阜通站', category: 'subway', lng: 116.4810, lat: 40.0010, scale: 8000 },
  { name: '东大桥站', category: 'subway', lng: 116.4600, lat: 39.9250, scale: 11000 },
  { name: '永安里站', category: 'subway', lng: 116.4580, lat: 39.9140, scale: 10000 },
  { name: '建国门站', category: 'subway', lng: 116.4350, lat: 39.9090, scale: 16000 },
  { name: '复兴门站', category: 'subway', lng: 116.3580, lat: 39.9090, scale: 14000 },
];

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
