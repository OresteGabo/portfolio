import type {
  BondAssumptions,
  BondPurchase,
  CashInjection,
  ModeledBondPurchase,
  ModeledCouponPayment,
  MonthlyProjection,
  ProjectionSummary,
} from "./types";

export const DEFAULT_ASSUMPTIONS: BondAssumptions = {
  monthlyContribution: 300_000,
  contributionPeriods: [
    {
      id: "default",
      amount: 300_000,
      startMonth: 1,
      endMonth: 240,
    },
  ],
  horizonYears: 20,
  startMonth: 7,
  startYear: 2026,
  tenorYears: 7,
  allowedTenors: [5, 7, 10, 15, 20, 25],
  tenorCouponRates: {
    "5": 0.11,
    "7": 0.115,
    "10": 0.12,
    "15": 0.129,
    "20": 0.131,
    "25": 0.1325,
  },
  annualCouponRate: 0.115,
  couponPaymentsPerYear: 2,
  reinvestmentRate: 1,
  auctionFillRate: 0.67,
  purchaseMinimum: 100_000,
  purchaseCharge: 11_000,
};

export const WITHHOLDING_TAX_RATE = 0.05;
export const MIN_ANNUAL_COUPON_RATE = 0.1065;
export const MAX_ANNUAL_COUPON_RATE = 0.135;
export const TREASURY_BOND_TENORS = [3, 5, 7, 10, 15, 20, 25] as const;
export const SIMULATION_TREASURY_BOND_TENORS = [5, 7, 10, 15, 20, 25] as const;
export const REPEATING_ISSUANCE_TENOR_CYCLE = [
  7, 10, 15, 20, 5, 7, 10, 25, 15, 10, 20, 7,
] as const;
export const SECONDARY_MARKET_COMMISSION_RATE = 0.00049;

function monthIndexFromIsoDate(
  assumptions: Pick<BondAssumptions, "startMonth" | "startYear">,
  isoDate: string,
) {
  const [year, month] = isoDate.split("-").map(Number);
  if (!year || !month) return null;

  return (year - assumptions.startYear) * 12 + month - assumptions.startMonth + 1;
}

export function purchaseToStartingLot(
  purchase: BondPurchase,
  assumptions: Pick<BondAssumptions, "startMonth" | "startYear">,
): ModeledBondPurchase | null {
  if (
    purchase.status !== "active" ||
    purchase.faceValue <= 0 ||
    purchase.couponRate <= 0
  ) {
    return null;
  }

  const maturityMonth = monthIndexFromIsoDate(assumptions, purchase.maturityDate);
  if (!maturityMonth || maturityMonth < 1) return null;

  const purchaseMonth =
    monthIndexFromIsoDate(
      assumptions,
      purchase.settlementDate || purchase.purchaseDate,
    ) ?? 0;
  const couponMonths = purchase.couponDates
    .map((date) => monthIndexFromIsoDate(assumptions, date))
    .filter((month): month is number => Boolean(month && month >= 1))
    .filter((month) => month <= maturityMonth);
  const couponSchedule = purchase.couponDates
    .map((date) => ({
      date,
      month: monthIndexFromIsoDate(assumptions, date),
    }))
    .filter(
      (payment): payment is { date: string; month: number } =>
        Boolean(payment.month && payment.month >= 1),
    )
    .filter((payment) => payment.month <= maturityMonth);
  const grossCouponAmount = purchase.faceValue * purchase.couponRate /
    Math.max(1, purchase.couponFrequency || 2);
  const netCouponAmount = grossCouponAmount * (1 - purchase.withholdingTaxRate);

  return {
    id: `actual-${purchase.id}`,
    purchaseMonth,
    purchaseDate: purchase.settlementDate || purchase.purchaseDate,
    maturityMonth,
    maturityDate: purchase.maturityDate,
    amount: purchase.faceValue,
    cashCost: purchase.amountInvested,
    tenorYears: purchase.tenorYears,
    annualCouponRate: purchase.couponRate,
    netAnnualCouponRate: purchase.couponRate * (1 - purchase.withholdingTaxRate),
    couponFrequency: Math.max(1, purchase.couponFrequency || 2),
    couponMonths,
    couponSchedule,
    grossCouponAmount,
    netCouponAmount,
  };
}

export function earliestActivePurchaseMonth(purchases: BondPurchase[]) {
  return purchases
    .filter(
      (purchase) =>
        purchase.status === "active" &&
        purchase.faceValue > 0 &&
        purchase.couponRate > 0,
    )
    .map((purchase) => {
      const [year, month] = (
        purchase.settlementDate || purchase.purchaseDate
      ).split("-").map(Number);

      return year && month ? { year, month } : null;
    })
    .filter((date): date is { year: number; month: number } => Boolean(date))
    .sort((a, b) => a.year - b.year || a.month - b.month)[0] ?? null;
}

function modulo(value: number, divisor: number) {
  return ((value % divisor) + divisor) % divisor;
}

function repeatingAuctionTenor(calendarYear: number, calendarMonth: number) {
  const monthOffset = (calendarYear - 2026) * 12 + (calendarMonth - 7);
  return REPEATING_ISSUANCE_TENOR_CYCLE[
    modulo(monthOffset, REPEATING_ISSUANCE_TENOR_CYCLE.length)
  ];
}

function couponRateForTenor(assumptions: BondAssumptions, tenorYears: number) {
  const configured = assumptions.tenorCouponRates?.[String(tenorYears)];
  if (Number.isFinite(configured) && configured > 0) return configured;
  if (tenorYears === assumptions.tenorYears) return assumptions.annualCouponRate;
  return DEFAULT_ASSUMPTIONS.tenorCouponRates[String(tenorYears)] ??
    assumptions.annualCouponRate;
}

export function calculateProjection(
  assumptions: BondAssumptions,
  cashInjections: CashInjection[] = [],
  startingLots?: ModeledBondPurchase[],
): MonthlyProjection[] {
  const totalMonths = Math.max(1, Math.round(assumptions.horizonYears * 12));
  const contributionPeriods =
    assumptions.contributionPeriods?.length > 0
      ? assumptions.contributionPeriods
      : [
          {
            id: "legacy-monthly-contribution",
            amount: assumptions.monthlyContribution,
            startMonth: 1,
            endMonth: totalMonths,
          },
        ];
  const paymentsPerYear = Math.max(1, assumptions.couponPaymentsPerYear);
  const paymentInterval = 12 / paymentsPerYear;
  const auctionFillRate = Math.max(0, Math.min(1, assumptions.auctionFillRate));
  const allowedTenors =
    assumptions.allowedTenors?.length > 0
      ? assumptions.allowedTenors
      : DEFAULT_ASSUMPTIONS.allowedTenors;
  const sanitizeCouponRate = (rate: number) =>
    Math.min(MAX_ANNUAL_COUPON_RATE, Math.max(MIN_ANNUAL_COUPON_RATE, rate));

  const modeledPurchaseDate = (month: number) => {
    const date = new Date(
      assumptions.startYear,
      assumptions.startMonth - 1 + month - 1,
      5,
    );
    return [
      date.getFullYear(),
      String(date.getMonth() + 1).padStart(2, "0"),
      "05",
    ].join("-");
  };
  const makeLot = (
    purchaseMonth: number,
    amount: number,
    id: string,
    tenorYears = assumptions.tenorYears,
    couponRate = assumptions.annualCouponRate,
    cashCost = amount,
  ): ModeledBondPurchase => {
    const annualCouponRate = sanitizeCouponRate(couponRate);
    const maturityMonth = purchaseMonth + tenorYears * 12;
    return {
      id,
      purchaseMonth,
      purchaseDate: modeledPurchaseDate(purchaseMonth),
      maturityMonth,
      maturityDate: modeledPurchaseDate(maturityMonth),
      amount,
      cashCost,
      tenorYears,
      annualCouponRate,
      netAnnualCouponRate: annualCouponRate * (1 - WITHHOLDING_TAX_RATE),
      couponFrequency: paymentsPerYear,
    };
  };

  const realLots = startingLots
    ? startingLots
        .filter((lot) => lot.amount > 0 && lot.maturityMonth >= 1)
        .map((lot) => ({ ...lot }))
    : null;
  const openingRealLots = realLots
    ? realLots.filter((lot) => lot.purchaseMonth <= 1)
    : [];
  const openingRealCashCost = openingRealLots.reduce(
    (total, lot) => total + (lot.cashCost ?? lot.amount),
    0,
  );
  let activeLots: ModeledBondPurchase[] = realLots ? openingRealLots : [];
  let pendingRealLots = realLots
    ? realLots.filter((lot) => lot.purchaseMonth > 1)
    : [];
  let cashBalance = 0;
  let totalContributions = 0;
  let totalCoupons = 0;
  let totalReinvested = 0;

  return Array.from({ length: totalMonths }, (_, index) => {
    const month = index + 1;
    const realLotsStartingThisMonth = pendingRealLots.filter(
      (lot) => lot.purchaseMonth === month,
    );
    if (realLotsStartingThisMonth.length > 0) {
      activeLots.push(...realLotsStartingThisMonth);
      pendingRealLots = pendingRealLots.filter(
        (lot) => lot.purchaseMonth !== month,
      );
    }
    const realBondPurchase =
      month === 1
        ? openingRealCashCost
        : realLotsStartingThisMonth.reduce(
            (total, lot) => total + (lot.cashCost ?? lot.amount),
            0,
          );
    const calendarDate = new Date(
      assumptions.startYear,
      assumptions.startMonth - 1 + index,
      1,
    );
    const auctionTenorYears = repeatingAuctionTenor(
      calendarDate.getFullYear(),
      calendarDate.getMonth() + 1,
    );
    const auctionEligible = allowedTenors.includes(auctionTenorYears);
    const openingPortfolio = activeLots.reduce(
      (total, lot) => total + lot.amount,
      0,
    );
    const openingCashBalance = cashBalance;
    const personalContribution = contributionPeriods.reduce(
      (total, period) =>
        month >= period.startMonth && month <= period.endMonth
          ? total + Math.max(0, period.amount)
          : total,
      0,
    );
    const monthlyInjections = cashInjections.filter(
      (injection) => injection.month === month,
    );
    const cashInjection = monthlyInjections.reduce(
      (total, injection) => total + injection.amount,
      0,
    );
    const couponPayments: ModeledCouponPayment[] = activeLots
      .map((lot) => {
        const scheduledCoupon = lot.couponSchedule?.find(
          (payment) => payment.month === month,
        );
        if (lot.couponSchedule) {
          return scheduledCoupon && month <= lot.maturityMonth
            ? { lot, scheduledCoupon }
            : null;
        }

        const modeledCouponDue =
          Number.isInteger(paymentInterval) &&
          month > lot.purchaseMonth &&
          month <= lot.maturityMonth &&
          (month - lot.purchaseMonth) % paymentInterval === 0;

        return modeledCouponDue ? { lot, scheduledCoupon: null } : null;
      })
      .filter(
        (
          payment,
        ): payment is {
          lot: ModeledBondPurchase;
          scheduledCoupon: { month: number; date: string } | null;
        } => Boolean(payment),
      )
      .map(({ lot, scheduledCoupon }) => {
        const grossCouponAmount =
          lot.grossCouponAmount ??
          lot.amount * (lot.annualCouponRate / lot.couponFrequency);
        const couponAmount =
          lot.netCouponAmount ??
          lot.amount * (lot.netAnnualCouponRate / lot.couponFrequency);

        return {
          lotId: lot.id,
          purchaseDate: lot.purchaseDate,
          couponDate: scheduledCoupon?.date,
          amountInvested: lot.amount,
          grossCouponAmount,
          couponAmount,
        };
      });
    const couponPayment = couponPayments.reduce(
      (total, payment) => total + payment.couponAmount,
      0,
    );
    const maturedLots = activeLots.filter(
      (lot) => lot.maturityMonth === month,
    );
    const maturedPrincipal = maturedLots.reduce(
      (total, lot) => total + lot.amount,
      0,
    );
    activeLots = activeLots.filter((lot) => lot.maturityMonth !== month);
    const reinvestedCoupon = couponPayment * assumptions.reinvestmentRate;
    const availableCash =
      Math.round(
        (openingCashBalance +
          personalContribution +
          cashInjection +
          reinvestedCoupon +
          maturedPrincipal) *
          100,
      ) / 100;
    const intendedBondBid = auctionEligible
      ? Math.floor((availableCash + 0.001) / assumptions.purchaseMinimum) *
        assumptions.purchaseMinimum
      : 0;
    const filledBondPurchase =
      Math.floor(
        (intendedBondBid * auctionFillRate + 0.001) /
          assumptions.purchaseMinimum,
      ) * assumptions.purchaseMinimum;
    const newBondPurchase = Math.min(intendedBondBid, filledBondPurchase);
    const purchaseCharge =
      newBondPurchase > 0 ? Math.max(0, assumptions.purchaseCharge ?? 0) : 0;
    const unfilledBondBid = intendedBondBid - newBondPurchase;

    const newBondPurchaseLot =
      newBondPurchase > 0
        ? makeLot(
            month,
            newBondPurchase,
            `modeled-${month}`,
            auctionTenorYears,
            couponRateForTenor(assumptions, auctionTenorYears),
            newBondPurchase + purchaseCharge,
          )
        : null;
    if (newBondPurchaseLot) activeLots.push(newBondPurchaseLot);
    cashBalance =
      Math.round((availableCash - newBondPurchase) * 100) / 100;
    const portfolio = activeLots.reduce(
      (total, lot) => total + lot.amount,
      0,
    );
    totalContributions +=
      personalContribution + cashInjection + realBondPurchase + purchaseCharge;
    totalCoupons += couponPayment;
    totalReinvested += reinvestedCoupon;
    const annualBondPassiveIncome = activeLots.reduce(
      (total, lot) => total + lot.amount * lot.netAnnualCouponRate,
      0,
    );

    return {
      month,
      year: Math.ceil(month / 12),
      monthInYear: ((month - 1) % 12) + 1,
      calendarMonth: calendarDate.getMonth() + 1,
      calendarYear: calendarDate.getFullYear(),
      openingPortfolio,
      openingCashBalance,
      personalContribution,
      cashInjection,
      cashInjectionLabels: monthlyInjections.map((injection) => injection.label),
      realBondPurchase,
      purchaseCharge,
      auctionTenorYears,
      auctionEligible,
      couponPayment,
      couponPayments,
      reinvestedCoupon,
      maturedPrincipal,
      availableCash,
      intendedBondBid,
      unfilledBondBid,
      newBondPurchase,
      newBondPurchaseLot,
      activeBondCount: activeLots.length,
      closingCashBalance: cashBalance,
      closingPortfolio: portfolio,
      totalAccountValue: portfolio + cashBalance,
      totalContributions,
      totalCoupons,
      totalReinvested,
      annualBondPassiveIncome,
      annualPassiveIncome: annualBondPassiveIncome,
      monthlyPassiveIncome: annualBondPassiveIncome / 12,
    };
  });
}

export function summarizeProjection(
  projection: MonthlyProjection[],
  assumptions: BondAssumptions,
): ProjectionSummary {
  const final = projection.at(-1);
  const firstMonthAt = (amount: number) =>
    projection.find((row) => row.totalAccountValue >= amount)?.month ?? null;
  const crossoverMonth =
    projection.find(
      (row) => row.annualPassiveIncome > row.personalContribution * 12,
    )?.month ?? null;

  return {
    finalPortfolio: final?.closingPortfolio ?? 0,
    finalCashBalance: final?.closingCashBalance ?? 0,
    finalAccountValue: final?.totalAccountValue ?? 0,
    totalContributions: final?.totalContributions ?? 0,
    totalCoupons: final?.totalCoupons ?? 0,
    totalReinvested: final?.totalReinvested ?? 0,
    annualBondPassiveIncome: final?.annualBondPassiveIncome ?? 0,
    annualPassiveIncome: final?.annualPassiveIncome ?? 0,
    monthlyPassiveIncome: final?.monthlyPassiveIncome ?? 0,
    milestone50m: firstMonthAt(50_000_000),
    milestone100m: firstMonthAt(100_000_000),
    milestone200m: firstMonthAt(200_000_000),
    passiveIncomeCrossoverYear: crossoverMonth
      ? Math.ceil(crossoverMonth / 12)
      : null,
  };
}

export function formatRwf(value: number, compact = false): string {
  if (compact && Math.abs(value) >= 1_000_000_000 && Math.abs(value) < 1_000_000_000_000) {
    const billions = Math.trunc((value / 1_000_000_000) * 100) / 100;

    return `${new Intl.NumberFormat("en-RW", {
      style: "currency",
      currency: "RWF",
      currencyDisplay: "code",
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(billions)}B`;
  }

  return new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency: "RWF",
    currencyDisplay: "code",
    notation: compact ? "compact" : "standard",
    maximumFractionDigits: compact ? 1 : 0,
    roundingMode: compact ? "trunc" : undefined,
  }).format(value);
}

export function formatPercent(value: number, maximumFractionDigits = 1): string {
  return new Intl.NumberFormat("en", {
    style: "percent",
    maximumFractionDigits,
  }).format(value);
}
