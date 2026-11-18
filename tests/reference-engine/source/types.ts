export type BondAssumptions = {
  monthlyContribution: number;
  contributionPeriods: ContributionPeriod[];
  horizonYears: number;
  startMonth: number;
  startYear: number;
  tenorYears: number;
  allowedTenors: number[];
  tenorCouponRates: Record<string, number>;
  annualCouponRate: number;
  couponPaymentsPerYear: number;
  reinvestmentRate: number;
  auctionFillRate: number;
  purchaseMinimum: number;
  purchaseCharge: number;
};

export type ContributionPeriod = {
  id: string;
  amount: number;
  startMonth: number;
  endMonth: number;
};

export type CashInjection = {
  id: string;
  label: string;
  month: number;
  amount: number;
};

export type ModeledBondPurchase = {
  id: string;
  purchaseMonth: number;
  purchaseDate: string;
  maturityMonth: number;
  maturityDate: string;
  amount: number;
  cashCost?: number;
  tenorYears: number;
  annualCouponRate: number;
  netAnnualCouponRate: number;
  couponFrequency: number;
  couponMonths?: number[];
  couponSchedule?: { month: number; date: string }[];
  grossCouponAmount?: number;
  netCouponAmount?: number;
};

export type ModeledCouponPayment = {
  lotId: string;
  purchaseDate: string;
  couponDate?: string;
  amountInvested: number;
  grossCouponAmount?: number;
  couponAmount: number;
};

export type MonthlyProjection = {
  month: number;
  year: number;
  monthInYear: number;
  calendarMonth: number;
  calendarYear: number;
  openingPortfolio: number;
  openingCashBalance: number;
  personalContribution: number;
  cashInjection: number;
  cashInjectionLabels: string[];
  realBondPurchase: number;
  purchaseCharge: number;
  auctionTenorYears: number;
  auctionEligible: boolean;
  couponPayment: number;
  couponPayments: ModeledCouponPayment[];
  reinvestedCoupon: number;
  maturedPrincipal: number;
  availableCash: number;
  intendedBondBid: number;
  unfilledBondBid: number;
  newBondPurchase: number;
  newBondPurchaseLot: ModeledBondPurchase | null;
  activeBondCount: number;
  closingCashBalance: number;
  closingPortfolio: number;
  totalAccountValue: number;
  totalContributions: number;
  totalCoupons: number;
  totalReinvested: number;
  annualBondPassiveIncome: number;
  annualPassiveIncome: number;
  monthlyPassiveIncome: number;
};

export type ProjectionSummary = {
  finalPortfolio: number;
  finalCashBalance: number;
  finalAccountValue: number;
  totalContributions: number;
  totalCoupons: number;
  totalReinvested: number;
  annualBondPassiveIncome: number;
  annualPassiveIncome: number;
  monthlyPassiveIncome: number;
  milestone50m: number | null;
  milestone100m: number | null;
  milestone200m: number | null;
  passiveIncomeCrossoverYear: number | null;
};

export type BondPurchase = {
  id: string;
  instrumentType:
    | "treasury"
    | "government"
    | "corporate"
    | "municipal"
    | "other";
  issuer: string;
  currency: string;
  market: "primary" | "secondary" | "other";
  purchaseDate: string;
  settlementDate: string;
  bondName: string;
  isin: string;
  tenorYears: number;
  faceValue: number;
  pricePercent: number;
  accruedInterestPaid: number;
  feesPaid: number;
  amountInvested: number;
  couponRate: number;
  withholdingTaxRate: number;
  maturityDate: string;
  firstCouponDate: string;
  couponDates: string[];
  couponFrequency: number;
  scheduleConfidence: "confirmed" | "estimated";
  broker: string;
  accountReference: string;
  sourceUrl: string;
  status: "submitted" | "active" | "sold" | "matured";
  notes: string;
  createdAt: string;
};

export type BondPurchaseInput = Omit<BondPurchase, "id" | "createdAt">;

export type AccountPortfolioSummary = {
  equityActivity?: { purchaseCount: number; cashCost: number; latestSecurity: string | null };
  purchaseCount: number;
  principalByCurrency: { currency: string; amount: number }[];
  latestPurchase: Pick<BondPurchase, "bondName" | "isin" | "purchaseDate" | "createdAt"> | null;
};

export type EquityHolding = {
  id: string;
  instrumentType: "equity";
  exchange: string;
  securityName: string;
  ticker: string;
  isin: string;
  tradeDate: string;
  settlementDate: string;
  shares: number;
  pricePerShare: number;
  grossConsideration: number;
  brokerageCommission: number;
  rseTransactionFee: number;
  csdTransactionLevy: number;
  cmaTransactionFee: number;
  totalCharges: number;
  netAmountPayable: number;
  broker: string;
  contractNote: string;
  cdsAccountNo: string;
  cdsReference: string;
  tradeReportId: string;
  currency: string;
  status: "active" | "sold";
  notes: string;
  createdAt: string;
};

export type EquityHoldingInput = Omit<EquityHolding, "id" | "createdAt">;
