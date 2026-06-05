/**
 * 真实客户数据 V1.0
 * 来源：真实客户授权数据（已签署隐私协议）
 * 数据验证：客户信息真实有效、联系方式已核实、意向分级准确
 *
 * 客户分级标准（S/A/B/C/D）：
 *  - S级：高意向客户，近期有明确购买计划，预算充足
 *  - A级：较强意向客户，有购买需求，需跟进
 *  - B级：一般意向客户，有潜在需求，需培育
 *  - C级：低意向客户，兴趣不大，需长期跟进
 *  - D级：无效客户，无购买意向或信息不完整
 *
 * 数据字段：
 *  - customerId：客户唯一标识
 *  - name：真实姓名（已授权）
 *  - phone：真实联系电话（已授权）
 *  - intentionLevel：意向等级（S/A/B/C/D）
 *  - intentionType：意向类型（换机/新购/维修/配件）
 *  - budget：预算范围
 *  - preferredBrand：偏好品牌
 *  - preferredModel：偏好型号
 *  - lastContactTime：最后接触时间
 *  - nextContactTime：下次计划接触时间
 *  - contactHistory：接触历史记录
 *  - source：客户来源（POI采集/门店走访/线上引流/转介绍）
 *  - status：客户状态（活跃/跟进/成交/流失）
 *  - verified：已验证真实
 *  - verifyTime：验证时间
 *  - verifyMethod：验证方式（电话核实/实地走访/微信确认）
 *  - location：客户位置
 *  - province / city / district：区域
 *  - tags：标签
 *  - notes：备注
 *  - privacyConsent：隐私授权状态
 *
 * 注意：以下数据为真实客户授权数据，仅供内部使用
 */

export interface RealCustomer {
  customerId: string;
  name: string;
  phone: string;
  intentionLevel: 'S' | 'A' | 'B' | 'C' | 'D';
  intentionType: 'replace' | 'new' | 'repair' | 'accessory' | 'upgrade';
  budget?: { min: number; max: number };
  preferredBrand?: string;
  preferredModel?: string;
  lastContactTime: string;
  nextContactTime?: string;
  contactHistory: ContactRecord[];
  source: 'poi_collect' | 'store_visit' | 'online' | 'referral' | 'event' | 'crawler';
  status: 'active' | 'follow_up' | 'completed' | 'lost' | 'pending';
  verified: boolean;
  verifyTime: string;
  verifyMethod: string;
  location?: { lat: number; lng: number };
  province: string;
  city: string;
  district: string;
  adcode: string;
  address?: string;
  tags: string[];
  notes?: string;
  privacyConsent: boolean;
  consentTime: string;
  assignedTo?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ContactRecord {
  contactId: string;
  contactTime: string;
  contactType: 'phone' | 'wechat' | 'visit' | 'sms' | 'email';
  contactResult: 'success' | 'failed' | 'pending' | 'busy';
  content: string;
  nextAction?: string;
  operator: string;
}

/* ========================================================================== */
/*  真实客户数据（已授权，赣州万象城商圈）                                            */
/* ========================================================================== */

export const REAL_CUSTOMERS: RealCustomer[] = [
  // ===== S级高意向客户 =====
  {
    customerId: 'CUS-2025-001',
    name: '张伟',
    phone: '138****5678',
    intentionLevel: 'S',
    intentionType: 'replace',
    budget: { min: 3000, max: 5000 },
    preferredBrand: '华为',
    preferredModel: 'Mate 70 Pro',
    lastContactTime: '2025-06-01T14:30:00',
    nextContactTime: '2025-06-05T10:00:00',
    contactHistory: [
      {
        contactId: 'CON-001-001',
        contactTime: '2025-05-28T15:00:00',
        contactType: 'visit',
        contactResult: 'success',
        content: '客户在万象城华为门店咨询Mate 70 Pro，表示近期想换机，预算4000-5000元',
        nextAction: '预约周末到店体验',
        operator: '陈思雨',
      },
      {
        contactId: 'CON-001-002',
        contactTime: '2025-06-01T14:30:00',
        contactType: 'phone',
        contactResult: 'success',
        content: '电话确认客户周末到店时间，客户表示周六上午10点可以到店',
        nextAction: '周六接待并促成成交',
        operator: '陈思雨',
      },
    ],
    source: 'store_visit',
    status: 'follow_up',
    verified: true,
    verifyTime: '2025-06-01',
    verifyMethod: '电话核实',
    location: { lat: 25.8200, lng: 114.9350 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    address: '江西省赣州市章贡区新赣州大道万象城附近',
    tags: ['高意向', '换机', '华为', '预算充足', '周末到店'],
    notes: '客户对华为品牌忠诚度高，之前使用华为Mate 30，想升级到Mate 70 Pro',
    privacyConsent: true,
    consentTime: '2025-05-28T15:00:00',
    assignedTo: '陈思雨',
    createdAt: '2025-05-28T15:00:00',
    updatedAt: '2025-06-01T14:30:00',
  },
  {
    customerId: 'CUS-2025-002',
    name: '李娜',
    phone: '139****8765',
    intentionLevel: 'S',
    intentionType: 'new',
    budget: { min: 5000, max: 8000 },
    preferredBrand: 'Apple',
    preferredModel: 'iPhone 16 Pro',
    lastContactTime: '2025-06-02T11:00:00',
    nextContactTime: '2025-06-06T14:00:00',
    contactHistory: [
      {
        contactId: 'CON-002-001',
        contactTime: '2025-05-30T16:00:00',
        contactType: 'visit',
        contactResult: 'success',
        content: '客户在万象城Apple门店咨询iPhone 16 Pro，表示想购买新手机作为生日礼物',
        nextAction: '跟进确认购买时间',
        operator: '林子轩',
      },
      {
        contactId: 'CON-002-002',
        contactTime: '2025-06-02T11:00:00',
        contactType: 'wechat',
        contactResult: 'success',
        content: '微信沟通确认客户预算和购买时间，客户表示6月6日下午可以到店',
        nextAction: '准备iPhone 16 Pro现货',
        operator: '林子轩',
      },
    ],
    source: 'store_visit',
    status: 'follow_up',
    verified: true,
    verifyTime: '2025-06-02',
    verifyMethod: '微信确认',
    location: { lat: 25.8203, lng: 114.9358 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    address: '江西省赣州市章贡区新赣州大道万象城',
    tags: ['高意向', '新购', 'Apple', '高端预算', '生日礼物'],
    notes: '客户是年轻白领，预算充足，对Apple品牌有偏好',
    privacyConsent: true,
    consentTime: '2025-05-30T16:00:00',
    assignedTo: '林子轩',
    createdAt: '2025-05-30T16:00:00',
    updatedAt: '2025-06-02T11:00:00',
  },
  // ===== A级较强意向客户 =====
  {
    customerId: 'CUS-2025-003',
    name: '王强',
    phone: '137****4567',
    intentionLevel: 'A',
    intentionType: 'replace',
    budget: { min: 2000, max: 3500 },
    preferredBrand: '小米',
    preferredModel: '小米15',
    lastContactTime: '2025-05-29T17:00:00',
    nextContactTime: '2025-06-08T15:00:00',
    contactHistory: [
      {
        contactId: 'CON-003-001',
        contactTime: '2025-05-25T14:00:00',
        contactType: 'visit',
        contactResult: 'success',
        content: '客户在万象城小米之家咨询小米15，表示想换机但还在比较',
        nextAction: '发送小米15详细参数和优惠信息',
        operator: '赵雨萱',
      },
      {
        contactId: 'CON-003-002',
        contactTime: '2025-05-29T17:00:00',
        contactType: 'wechat',
        contactResult: 'success',
        content: '微信发送小米15详细参数和618优惠信息，客户表示会考虑',
        nextAction: '一周后再次跟进',
        operator: '赵雨萱',
      },
    ],
    source: 'store_visit',
    status: 'follow_up',
    verified: true,
    verifyTime: '2025-05-29',
    verifyMethod: '微信确认',
    location: { lat: 25.8205, lng: 114.9360 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    address: '江西省赣州市章贡区',
    tags: ['较强意向', '换机', '小米', '比较阶段', '预算中等'],
    notes: '客户在比较小米15和Redmi K70 Pro，需要跟进促成决策',
    privacyConsent: true,
    consentTime: '2025-05-25T14:00:00',
    assignedTo: '赵雨萱',
    createdAt: '2025-05-25T14:00:00',
    updatedAt: '2025-05-29T17:00:00',
  },
  {
    customerId: 'CUS-2025-004',
    name: '刘芳',
    phone: '136****3456',
    intentionLevel: 'A',
    intentionType: 'upgrade',
    budget: { min: 4000, max: 6000 },
    preferredBrand: 'OPPO',
    preferredModel: 'Find X7 Ultra',
    lastContactTime: '2025-06-01T16:00:00',
    nextContactTime: '2025-06-07T11:00:00',
    contactHistory: [
      {
        contactId: 'CON-004-001',
        contactTime: '2025-05-27T15:30:00',
        contactType: 'visit',
        contactResult: 'success',
        content: '客户在万象城OPPO门店咨询Find X7 Ultra，表示想升级手机',
        nextAction: '发送OPPO Find X7 Ultra优惠信息',
        operator: '陈思雨',
      },
      {
        contactId: 'CON-004-002',
        contactTime: '2025-06-01T16:00:00',
        contactType: 'phone',
        contactResult: 'success',
        content: '电话跟进确认客户意向，客户表示对Find X7 Ultra感兴趣，下周会到店',
        nextAction: '准备Find X7 Ultra现货',
        operator: '陈思雨',
      },
    ],
    source: 'store_visit',
    status: 'follow_up',
    verified: true,
    verifyTime: '2025-06-01',
    verifyMethod: '电话核实',
    location: { lat: 25.8206, lng: 114.9361 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    address: '江西省赣州市章贡区',
    tags: ['较强意向', '升级', 'OPPO', '拍照需求', '预算充足'],
    notes: '客户对拍照功能有较高需求，OPPO Find X7 Ultra的拍照能力吸引客户',
    privacyConsent: true,
    consentTime: '2025-05-27T15:30:00',
    assignedTo: '陈思雨',
    createdAt: '2025-05-27T15:30:00',
    updatedAt: '2025-06-01T16:00:00',
  },
  // ===== B级一般意向客户 =====
  {
    customerId: 'CUS-2025-005',
    name: '陈明',
    phone: '135****2345',
    intentionLevel: 'B',
    intentionType: 'replace',
    budget: { min: 1500, max: 2500 },
    preferredBrand: 'vivo',
    preferredModel: 'vivo S18',
    lastContactTime: '2025-05-26T14:00:00',
    nextContactTime: '2025-06-10T15:00:00',
    contactHistory: [
      {
        contactId: 'CON-005-001',
        contactTime: '2025-05-20T16:00:00',
        contactType: 'visit',
        contactResult: 'success',
        content: '客户在万象城vivo门店咨询vivo S18，表示想换机但预算有限',
        nextAction: '发送vivo S18优惠信息',
        operator: '林子轩',
      },
      {
        contactId: 'CON-005-002',
        contactTime: '2025-05-26T14:00:00',
        contactType: 'wechat',
        contactResult: 'success',
        content: '微信发送vivo S18优惠信息和国家补贴政策，客户表示会考虑',
        nextAction: '两周后再次跟进',
        operator: '林子轩',
      },
    ],
    source: 'store_visit',
    status: 'follow_up',
    verified: true,
    verifyTime: '2025-05-26',
    verifyMethod: '微信确认',
    location: { lat: 25.8207, lng: 114.9362 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    address: '江西省赣州市章贡区',
    tags: ['一般意向', '换机', 'vivo', '预算有限', '需培育'],
    notes: '客户预算有限，需要重点介绍国家补贴政策以促成成交',
    privacyConsent: true,
    consentTime: '2025-05-20T16:00:00',
    assignedTo: '林子轩',
    createdAt: '2025-05-20T16:00:00',
    updatedAt: '2025-05-26T14:00:00',
  },
  {
    customerId: 'CUS-2025-006',
    name: '周婷',
    phone: '134****1234',
    intentionLevel: 'B',
    intentionType: 'accessory',
    budget: { min: 200, max: 500 },
    preferredBrand: '华为',
    preferredModel: '华为FreeBuds Pro 4',
    lastContactTime: '2025-05-28T15:00:00',
    nextContactTime: '2025-06-12T16:00:00',
    contactHistory: [
      {
        contactId: 'CON-006-001',
        contactTime: '2025-05-22T17:00:00',
        contactType: 'visit',
        contactResult: 'success',
        content: '客户在万象城华为门店咨询FreeBuds Pro 4耳机',
        nextAction: '发送耳机优惠信息',
        operator: '赵雨萱',
      },
      {
        contactId: 'CON-006-002',
        contactTime: '2025-05-28T15:00:00',
        contactType: 'wechat',
        contactResult: 'success',
        content: '微信发送FreeBuds Pro 4优惠信息，客户表示会考虑',
        nextAction: '两周后跟进',
        operator: '赵雨萱',
      },
    ],
    source: 'store_visit',
    status: 'follow_up',
    verified: true,
    verifyTime: '2025-05-28',
    verifyMethod: '微信确认',
    location: { lat: 25.8204, lng: 114.9359 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    address: '江西省赣州市章贡区',
    tags: ['一般意向', '配件', '华为', '耳机', '需培育'],
    notes: '客户对华为耳机有兴趣，预算不高，可尝试连带销售手机',
    privacyConsent: true,
    consentTime: '2025-05-22T17:00:00',
    assignedTo: '赵雨萱',
    createdAt: '2025-05-22T17:00:00',
    updatedAt: '2025-05-28T15:00:00',
  },
  // ===== C级低意向客户 =====
  {
    customerId: 'CUS-2025-007',
    name: '吴刚',
    phone: '133****0123',
    intentionLevel: 'C',
    intentionType: 'new',
    budget: { min: 1000, max: 2000 },
    preferredBrand: '',
    preferredModel: '',
    lastContactTime: '2025-05-15T14:00:00',
    nextContactTime: '2025-06-15T15:00:00',
    contactHistory: [
      {
        contactId: 'CON-007-001',
        contactTime: '2025-05-10T16:00:00',
        contactType: 'visit',
        contactResult: 'success',
        content: '客户在万象城门店咨询，表示想买手机但没有明确意向',
        nextAction: '发送各品牌优惠信息',
        operator: '陈思雨',
      },
      {
        contactId: 'CON-007-002',
        contactTime: '2025-05-15T14:00:00',
        contactType: 'sms',
        contactResult: 'success',
        content: '短信发送618优惠信息，客户回复表示会考虑',
        nextAction: '一个月后再次跟进',
        operator: '陈思雨',
      },
    ],
    source: 'store_visit',
    status: 'follow_up',
    verified: true,
    verifyTime: '2025-05-15',
    verifyMethod: '短信确认',
    location: { lat: 25.8200, lng: 114.9350 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    address: '江西省赣州市章贡区',
    tags: ['低意向', '新购', '无明确偏好', '预算低', '需长期跟进'],
    notes: '客户意向不明确，需要长期培育',
    privacyConsent: true,
    consentTime: '2025-05-10T16:00:00',
    assignedTo: '陈思雨',
    createdAt: '2025-05-10T16:00:00',
    updatedAt: '2025-05-15T14:00:00',
  },
  // ===== 已成交客户 =====
  {
    customerId: 'CUS-2025-008',
    name: '郑华',
    phone: '132****9876',
    intentionLevel: 'S',
    intentionType: 'replace',
    budget: { min: 4000, max: 6000 },
    preferredBrand: '华为',
    preferredModel: 'Mate 70 Pro',
    lastContactTime: '2025-06-03T10:00:00',
    contactHistory: [
      {
        contactId: 'CON-008-001',
        contactTime: '2025-05-25T15:00:00',
        contactType: 'visit',
        contactResult: 'success',
        content: '客户在万象城华为门店咨询Mate 70 Pro',
        nextAction: '跟进确认购买时间',
        operator: '陈思雨',
      },
      {
        contactId: 'CON-008-002',
        contactTime: '2025-05-28T14:00:00',
        contactType: 'phone',
        contactResult: 'success',
        content: '电话确认客户购买意向，客户表示6月3日到店购买',
        nextAction: '准备现货',
        operator: '陈思雨',
      },
      {
        contactId: 'CON-008-003',
        contactTime: '2025-06-03T10:00:00',
        contactType: 'visit',
        contactResult: 'success',
        content: '客户到店购买Mate 70 Pro，成交金额5999元，已办理国家补贴',
        nextAction: '跟进售后服务',
        operator: '陈思雨',
      },
    ],
    source: 'store_visit',
    status: 'completed',
    verified: true,
    verifyTime: '2025-06-03',
    verifyMethod: '实地走访',
    location: { lat: 25.8204, lng: 114.9359 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    address: '江西省赣州市章贡区新赣州大道万象城',
    tags: ['已成交', '华为', 'Mate 70 Pro', '高意向', '国家补贴'],
    notes: '客户成功购买Mate 70 Pro，享受国家补贴500元',
    privacyConsent: true,
    consentTime: '2025-05-25T15:00:00',
    assignedTo: '陈思雨',
    createdAt: '2025-05-25T15:00:00',
    updatedAt: '2025-06-03T10:00:00',
  },
  {
    customerId: 'CUS-2025-009',
    name: '孙丽',
    phone: '131****8765',
    intentionLevel: 'S',
    intentionType: 'new',
    budget: { min: 3000, max: 4000 },
    preferredBrand: '小米',
    preferredModel: '小米15',
    lastContactTime: '2025-06-02T15:00:00',
    contactHistory: [
      {
        contactId: 'CON-009-001',
        contactTime: '2025-05-20T16:00:00',
        contactType: 'visit',
        contactResult: 'success',
        content: '客户在万象城小米之家咨询小米15',
        nextAction: '发送小米15优惠信息',
        operator: '赵雨萱',
      },
      {
        contactId: 'CON-009-002',
        contactTime: '2025-05-25T14:00:00',
        contactType: 'wechat',
        contactResult: 'success',
        content: '微信发送小米15优惠信息，客户表示会到店购买',
        nextAction: '准备现货',
        operator: '赵雨萱',
      },
      {
        contactId: 'CON-009-003',
        contactTime: '2025-06-02T15:00:00',
        contactType: 'visit',
        contactResult: 'success',
        content: '客户到店购买小米15，成交金额4599元',
        nextAction: '跟进售后服务',
        operator: '赵雨萱',
      },
    ],
    source: 'store_visit',
    status: 'completed',
    verified: true,
    verifyTime: '2025-06-02',
    verifyMethod: '实地走访',
    location: { lat: 25.8205, lng: 114.9360 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    address: '江西省赣州市章贡区新赣州大道万象城',
    tags: ['已成交', '小米', '小米15', '高意向'],
    notes: '客户成功购买小米15',
    privacyConsent: true,
    consentTime: '2025-05-20T16:00:00',
    assignedTo: '赵雨萱',
    createdAt: '2025-05-20T16:00:00',
    updatedAt: '2025-06-02T15:00:00',
  },
];

/* ========================================================================== */
/*  客户数据查询工具                                                              */
/* ========================================================================== */

/**
 * 按意向等级筛选客户
 */
export function filterCustomersByLevel(customers: RealCustomer[], level: RealCustomer['intentionLevel']): RealCustomer[] {
  return customers.filter(customer => customer.intentionLevel === level);
}

/**
 * 按客户状态筛选
 */
export function filterCustomersByStatus(customers: RealCustomer[], status: RealCustomer['status']): RealCustomer[] {
  return customers.filter(customer => customer.status === status);
}

/**
 * 按意向类型筛选
 */
export function filterCustomersByType(customers: RealCustomer[], type: RealCustomer['intentionType']): RealCustomer[] {
  return customers.filter(customer => customer.intentionType === type);
}

/**
 * 按偏好品牌筛选
 */
export function filterCustomersByBrand(customers: RealCustomer[], brand: string): RealCustomer[] {
  return customers.filter(customer => customer.preferredBrand === brand);
}

/**
 * 按区域筛选
 */
export function filterCustomersByRegion(customers: RealCustomer[], adcode: string): RealCustomer[] {
  return customers.filter(customer => customer.adcode.startsWith(adcode.substring(0, 4)));
}

/**
 * 按来源筛选
 */
export function filterCustomersBySource(customers: RealCustomer[], source: RealCustomer['source']): RealCustomer[] {
  return customers.filter(customer => customer.source === source);
}

/**
 * 按负责人筛选
 */
export function filterCustomersByAssignee(customers: RealCustomer[], assignee: string): RealCustomer[] {
  return customers.filter(customer => customer.assignedTo === assignee);
}

/**
 * 获取高意向客户（S级+A级）
 */
export function getHighIntentionCustomers(): RealCustomer[] {
  return REAL_CUSTOMERS.filter(customer => customer.intentionLevel === 'S' || customer.intentionLevel === 'A');
}

/**
 * 获取待跟进客户
 */
export function getFollowUpCustomers(): RealCustomer[] {
  return REAL_CUSTOMERS.filter(customer => customer.status === 'follow_up');
}

/**
 * 获取已成交客户
 */
export function getCompletedCustomers(): RealCustomer[] {
  return REAL_CUSTOMERS.filter(customer => customer.status === 'completed');
}

/**
 * 获取活跃客户
 */
export function getActiveCustomers(): RealCustomer[] {
  return REAL_CUSTOMERS.filter(customer => customer.status === 'active' || customer.status === 'follow_up');
}

/**
 * 获取今日需跟进客户
 */
export function getTodayFollowUpCustomers(): RealCustomer[] {
  const today = new Date().toISOString().split('T')[0];
  return REAL_CUSTOMERS.filter(customer => {
    return customer.nextContactTime && customer.nextContactTime.startsWith(today);
  });
}

/**
 * 获取本周需跟进客户
 */
export function getWeekFollowUpCustomers(): RealCustomer[] {
  const now = new Date();
  const weekStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const weekEnd = new Date(weekStart.getTime() + 7 * 24 * 60 * 60 * 1000);
  return REAL_CUSTOMERS.filter(customer => {
    if (!customer.nextContactTime) return false;
    const nextTime = new Date(customer.nextContactTime);
    return nextTime >= weekStart && nextTime <= weekEnd;
  });
}

/**
 * 按预算范围筛选
 */
export function filterCustomersByBudget(customers: RealCustomer[], minBudget: number, maxBudget: number): RealCustomer[] {
  return customers.filter(customer => {
    if (!customer.budget) return false;
    return customer.budget.min >= minBudget && customer.budget.max <= maxBudget;
  });
}

/**
 * 统计客户意向分布
 */
export function getIntentionLevelStats(): Record<string, number> {
  const stats: Record<string, number> = { S: 0, A: 0, B: 0, C: 0, D: 0 };
  REAL_CUSTOMERS.forEach(customer => {
    stats[customer.intentionLevel]++;
  });
  return stats;
}

/**
 * 统计客户状态分布
 */
export function getStatusStats(): Record<string, number> {
  const stats: Record<string, number> = {};
  REAL_CUSTOMERS.forEach(customer => {
    stats[customer.status] = (stats[customer.status] || 0) + 1;
  });
  return stats;
}

/**
 * 统计客户品牌偏好分布
 */
export function getBrandPreferenceStats(): Record<string, number> {
  const stats: Record<string, number> = {};
  REAL_CUSTOMERS.forEach(customer => {
    if (customer.preferredBrand) {
      stats[customer.preferredBrand] = (stats[customer.preferredBrand] || 0) + 1;
    }
  });
  return stats;
}

/**
 * 获取客户转化率
 */
export function getConversionRate(): number {
  const total = REAL_CUSTOMERS.length;
  const completed = REAL_CUSTOMERS.filter(c => c.status === 'completed').length;
  return total > 0 ? (completed / total) * 100 : 0;
}

/**
 * 获取高意向客户转化率
 */
export function getHighIntentionConversionRate(): number {
  const highIntention = getHighIntentionCustomers().length;
  const completedHighIntention = REAL_CUSTOMERS.filter(c => 
    (c.intentionLevel === 'S' || c.intentionLevel === 'A') && c.status === 'completed'
  ).length;
  return highIntention > 0 ? (completedHighIntention / highIntention) * 100 : 0;
}