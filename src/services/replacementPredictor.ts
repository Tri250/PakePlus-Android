/**
 * 5.3 AI 换机预测规则引擎
 * Input: { 机型, 购机日期, 价格区间, 维修次数 }
 * Output: { 预计换机窗口, 预警等级(红/黄/绿), 推荐话术 }
 */

export interface DeviceInput {
  brand: string;            // 品牌
  model: string;            // 机型
  purchaseDate: string;     // ISO 日期
  priceRange: number;       // 购入价格 (元)
  repairCount: number;      // 累计维修次数
  isFlagship?: boolean;     // 是否旗舰
}

export type AlertLevel = 'red' | 'yellow' | 'green';

export interface ReplacementPrediction {
  baseCycleMonths: number;     // 基础换机周期
  adjustedCycleMonths: number; // 调整后换机周期
  currentAgeMonths: number;    // 当前已使用月数
  purchaseWindow: {            // 换机窗口
    startDate: string;          // 窗口开始
    endDate: string;            // 窗口结束
    isInWindow: boolean;       // 当前是否已落入窗口
  };
  alertLevel: AlertLevel;       // 预警等级
  alertText: string;
  suggestedScript: string;      // 推荐话术
  recommendSubsidy: {           // 推荐补贴方案
    tradeInValue: number;
    govSubsidy: number;
    brandSubsidy: number;
    finalPrice: number;
  };
}

function baseCycleByPrice(price: number): number {
  if (price < 2000) return 18;
  if (price <= 5000) return 24;
  return 30;
}

function monthDiff(fromDate: Date, toDate: Date): number {
  const years = toDate.getFullYear() - fromDate.getFullYear();
  const months = toDate.getMonth() - fromDate.getMonth();
  const days = toDate.getDate() - fromDate.getDate();
  let total = years * 12 + months;
  if (days < 0) total -= 1;
  return total;
}

function addMonths(date: Date, months: number): Date {
  const d = new Date(date);
  d.setMonth(d.getMonth() + months);
  return d;
}

function generateScript(input: DeviceInput, prediction: ReplacementPrediction): string {
  const { model, brand } = input;
  const { alertLevel, currentAgeMonths, adjustedCycleMonths } = prediction;
  const remainMonths = adjustedCycleMonths - currentAgeMonths;

  if (alertLevel === 'red') {
    return `${model} 已陪伴 ${currentAgeMonths} 个月，超期 ${Math.abs(remainMonths)} 个月，强烈建议以旧换新 + 品牌专项补贴 ${prediction.recommendSubsidy.brandSubsidy} 元，新机到手更划算。`;
  }
  if (alertLevel === 'yellow') {
    return `${model} 已用 ${currentAgeMonths} 个月，再过 ${remainMonths} 个月进入换机黄金期，提前到店体验可享老用户专属价 + ${prediction.recommendSubsidy.govSubsidy} 元国补。`;
  }
  return `${model} 还在最佳使用期（已用 ${currentAgeMonths}/${adjustedCycleMonths} 月），建议 ${remainMonths} 个月后再触达，避免打扰。`;
}

export function predictReplacement(input: DeviceInput): ReplacementPrediction {
  const purchase = new Date(input.purchaseDate);
  const now = new Date('2026-06-05');
  const currentAgeMonths = monthDiff(purchase, now);

  let baseCycle = baseCycleByPrice(input.priceRange);
  if (input.isFlagship && baseCycle < 24) baseCycle = 24;
  if (input.repairCount > 3) baseCycle -= 3;

  const adjustedCycle = baseCycle;
  const remain = adjustedCycle - currentAgeMonths;

  let alertLevel: AlertLevel = 'green';
  let alertText = '正常使用中';
  if (currentAgeMonths >= adjustedCycle + 3) {
    alertLevel = 'red';
    alertText = `超期 ${currentAgeMonths - adjustedCycle} 个月 · 立即触达`;
  } else if (currentAgeMonths >= adjustedCycle - 3) {
    alertLevel = 'yellow';
    alertText = `即将进入换机窗口 · 剩 ${remain} 个月`;
  }

  const tradeInValue = Math.round(input.priceRange * 0.3);
  const govSubsidy = 500;
  const brandSubsidy = input.isFlagship ? 1000 : 600;
  const finalPrice = Math.max(2999, input.priceRange - tradeInValue - govSubsidy - brandSubsidy);

  const windowStart = addMonths(purchase, adjustedCycle - 3);
  const windowEnd = addMonths(purchase, adjustedCycle + 3);

  const base: Omit<ReplacementPrediction, 'suggestedScript'> = {
    baseCycleMonths: baseCycleByPrice(input.priceRange),
    adjustedCycleMonths: adjustedCycle,
    currentAgeMonths,
    purchaseWindow: {
      startDate: windowStart.toISOString().slice(0, 10),
      endDate: windowEnd.toISOString().slice(0, 10),
      isInWindow: currentAgeMonths >= adjustedCycle - 3 && currentAgeMonths <= adjustedCycle + 3,
    },
    alertLevel,
    alertText,
    recommendSubsidy: {
      tradeInValue,
      govSubsidy,
      brandSubsidy,
      finalPrice,
    },
  };

  return { ...base, suggestedScript: generateScript(input, { ...base, suggestedScript: '' }) };
}

export const ALERT_COLORS: Record<AlertLevel, { bg: string; text: string; ring: string; label: string }> = {
  red: { bg: 'bg-red-100', text: 'text-red-700', ring: 'ring-red-300', label: '红色预警' },
  yellow: { bg: 'bg-amber-100', text: 'text-amber-700', ring: 'ring-amber-300', label: '黄色预警' },
  green: { bg: 'bg-emerald-100', text: 'text-emerald-700', ring: 'ring-emerald-300', label: '正常使用' },
};
