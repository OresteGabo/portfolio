(function(global){
'use strict';
var modules={"./calculations": function(require,module,exports) {
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.SECONDARY_MARKET_COMMISSION_RATE = exports.REPEATING_ISSUANCE_TENOR_CYCLE = exports.SIMULATION_TREASURY_BOND_TENORS = exports.TREASURY_BOND_TENORS = exports.MAX_ANNUAL_COUPON_RATE = exports.MIN_ANNUAL_COUPON_RATE = exports.WITHHOLDING_TAX_RATE = exports.DEFAULT_ASSUMPTIONS = void 0;
exports.purchaseToStartingLot = purchaseToStartingLot;
exports.earliestActivePurchaseMonth = earliestActivePurchaseMonth;
exports.calculateProjection = calculateProjection;
exports.summarizeProjection = summarizeProjection;
exports.formatRwf = formatRwf;
exports.formatPercent = formatPercent;
exports.DEFAULT_ASSUMPTIONS = {
    monthlyContribution: 300000,
    contributionPeriods: [
        {
            id: "default",
            amount: 300000,
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
    purchaseMinimum: 100000,
    purchaseCharge: 11000,
};
exports.WITHHOLDING_TAX_RATE = 0.05;
exports.MIN_ANNUAL_COUPON_RATE = 0.1065;
exports.MAX_ANNUAL_COUPON_RATE = 0.135;
exports.TREASURY_BOND_TENORS = [3, 5, 7, 10, 15, 20, 25];
exports.SIMULATION_TREASURY_BOND_TENORS = [5, 7, 10, 15, 20, 25];
exports.REPEATING_ISSUANCE_TENOR_CYCLE = [
    7, 10, 15, 20, 5, 7, 10, 25, 15, 10, 20, 7,
];
exports.SECONDARY_MARKET_COMMISSION_RATE = 0.00049;
function monthIndexFromIsoDate(assumptions, isoDate) {
    const [year, month] = isoDate.split("-").map(Number);
    if (!year || !month)
        return null;
    return (year - assumptions.startYear) * 12 + month - assumptions.startMonth + 1;
}
function purchaseToStartingLot(purchase, assumptions) {
    if (purchase.status !== "active" ||
        purchase.faceValue <= 0 ||
        purchase.couponRate <= 0) {
        return null;
    }
    const maturityMonth = monthIndexFromIsoDate(assumptions, purchase.maturityDate);
    if (!maturityMonth || maturityMonth < 1)
        return null;
    const purchaseMonth = monthIndexFromIsoDate(assumptions, purchase.settlementDate || purchase.purchaseDate) ?? 0;
    const couponMonths = purchase.couponDates
        .map((date) => monthIndexFromIsoDate(assumptions, date))
        .filter((month) => Boolean(month && month >= 1))
        .filter((month) => month <= maturityMonth);
    const couponSchedule = purchase.couponDates
        .map((date) => ({
        date,
        month: monthIndexFromIsoDate(assumptions, date),
    }))
        .filter((payment) => Boolean(payment.month && payment.month >= 1))
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
function earliestActivePurchaseMonth(purchases) {
    return purchases
        .filter((purchase) => purchase.status === "active" &&
        purchase.faceValue > 0 &&
        purchase.couponRate > 0)
        .map((purchase) => {
        const [year, month] = (purchase.settlementDate || purchase.purchaseDate).split("-").map(Number);
        return year && month ? { year, month } : null;
    })
        .filter((date) => Boolean(date))
        .sort((a, b) => a.year - b.year || a.month - b.month)[0] ?? null;
}
function modulo(value, divisor) {
    return ((value % divisor) + divisor) % divisor;
}
function repeatingAuctionTenor(calendarYear, calendarMonth) {
    const monthOffset = (calendarYear - 2026) * 12 + (calendarMonth - 7);
    return exports.REPEATING_ISSUANCE_TENOR_CYCLE[modulo(monthOffset, exports.REPEATING_ISSUANCE_TENOR_CYCLE.length)];
}
function couponRateForTenor(assumptions, tenorYears) {
    const configured = assumptions.tenorCouponRates?.[String(tenorYears)];
    if (Number.isFinite(configured) && configured > 0)
        return configured;
    if (tenorYears === assumptions.tenorYears)
        return assumptions.annualCouponRate;
    return exports.DEFAULT_ASSUMPTIONS.tenorCouponRates[String(tenorYears)] ??
        assumptions.annualCouponRate;
}
function calculateProjection(assumptions, cashInjections = [], startingLots) {
    const totalMonths = Math.max(1, Math.round(assumptions.horizonYears * 12));
    const contributionPeriods = assumptions.contributionPeriods?.length > 0
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
    const allowedTenors = assumptions.allowedTenors?.length > 0
        ? assumptions.allowedTenors
        : exports.DEFAULT_ASSUMPTIONS.allowedTenors;
    const sanitizeCouponRate = (rate) => Math.min(exports.MAX_ANNUAL_COUPON_RATE, Math.max(exports.MIN_ANNUAL_COUPON_RATE, rate));
    const modeledPurchaseDate = (month) => {
        const date = new Date(assumptions.startYear, assumptions.startMonth - 1 + month - 1, 5);
        return [
            date.getFullYear(),
            String(date.getMonth() + 1).padStart(2, "0"),
            "05",
        ].join("-");
    };
    const makeLot = (purchaseMonth, amount, id, tenorYears = assumptions.tenorYears, couponRate = assumptions.annualCouponRate, cashCost = amount) => {
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
            netAnnualCouponRate: annualCouponRate * (1 - exports.WITHHOLDING_TAX_RATE),
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
    const openingRealCashCost = openingRealLots.reduce((total, lot) => total + (lot.cashCost ?? lot.amount), 0);
    let activeLots = realLots ? openingRealLots : [];
    let pendingRealLots = realLots
        ? realLots.filter((lot) => lot.purchaseMonth > 1)
        : [];
    let cashBalance = 0;
    let totalContributions = 0;
    let totalCoupons = 0;
    let totalReinvested = 0;
    return Array.from({ length: totalMonths }, (_, index) => {
        const month = index + 1;
        const realLotsStartingThisMonth = pendingRealLots.filter((lot) => lot.purchaseMonth === month);
        if (realLotsStartingThisMonth.length > 0) {
            activeLots.push(...realLotsStartingThisMonth);
            pendingRealLots = pendingRealLots.filter((lot) => lot.purchaseMonth !== month);
        }
        const realBondPurchase = month === 1
            ? openingRealCashCost
            : realLotsStartingThisMonth.reduce((total, lot) => total + (lot.cashCost ?? lot.amount), 0);
        const calendarDate = new Date(assumptions.startYear, assumptions.startMonth - 1 + index, 1);
        const auctionTenorYears = repeatingAuctionTenor(calendarDate.getFullYear(), calendarDate.getMonth() + 1);
        const auctionEligible = allowedTenors.includes(auctionTenorYears);
        const openingPortfolio = activeLots.reduce((total, lot) => total + lot.amount, 0);
        const openingCashBalance = cashBalance;
        const personalContribution = contributionPeriods.reduce((total, period) => month >= period.startMonth && month <= period.endMonth
            ? total + Math.max(0, period.amount)
            : total, 0);
        const monthlyInjections = cashInjections.filter((injection) => injection.month === month);
        const cashInjection = monthlyInjections.reduce((total, injection) => total + injection.amount, 0);
        const couponPayments = activeLots
            .map((lot) => {
            const scheduledCoupon = lot.couponSchedule?.find((payment) => payment.month === month);
            if (lot.couponSchedule) {
                return scheduledCoupon && month <= lot.maturityMonth
                    ? { lot, scheduledCoupon }
                    : null;
            }
            const modeledCouponDue = Number.isInteger(paymentInterval) &&
                month > lot.purchaseMonth &&
                month <= lot.maturityMonth &&
                (month - lot.purchaseMonth) % paymentInterval === 0;
            return modeledCouponDue ? { lot, scheduledCoupon: null } : null;
        })
            .filter((payment) => Boolean(payment))
            .map(({ lot, scheduledCoupon }) => {
            const grossCouponAmount = lot.grossCouponAmount ??
                lot.amount * (lot.annualCouponRate / lot.couponFrequency);
            const couponAmount = lot.netCouponAmount ??
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
        const couponPayment = couponPayments.reduce((total, payment) => total + payment.couponAmount, 0);
        const maturedLots = activeLots.filter((lot) => lot.maturityMonth === month);
        const maturedPrincipal = maturedLots.reduce((total, lot) => total + lot.amount, 0);
        activeLots = activeLots.filter((lot) => lot.maturityMonth !== month);
        const reinvestedCoupon = couponPayment * assumptions.reinvestmentRate;
        const availableCash = Math.round((openingCashBalance +
            personalContribution +
            cashInjection +
            reinvestedCoupon +
            maturedPrincipal) *
            100) / 100;
        const intendedBondBid = auctionEligible
            ? Math.floor((availableCash + 0.001) / assumptions.purchaseMinimum) *
                assumptions.purchaseMinimum
            : 0;
        const filledBondPurchase = Math.floor((intendedBondBid * auctionFillRate + 0.001) /
            assumptions.purchaseMinimum) * assumptions.purchaseMinimum;
        const newBondPurchase = Math.min(intendedBondBid, filledBondPurchase);
        const purchaseCharge = newBondPurchase > 0 ? Math.max(0, assumptions.purchaseCharge ?? 0) : 0;
        const unfilledBondBid = intendedBondBid - newBondPurchase;
        const newBondPurchaseLot = newBondPurchase > 0
            ? makeLot(month, newBondPurchase, `modeled-${month}`, auctionTenorYears, couponRateForTenor(assumptions, auctionTenorYears), newBondPurchase + purchaseCharge)
            : null;
        if (newBondPurchaseLot)
            activeLots.push(newBondPurchaseLot);
        cashBalance =
            Math.round((availableCash - newBondPurchase) * 100) / 100;
        const portfolio = activeLots.reduce((total, lot) => total + lot.amount, 0);
        totalContributions +=
            personalContribution + cashInjection + realBondPurchase + purchaseCharge;
        totalCoupons += couponPayment;
        totalReinvested += reinvestedCoupon;
        const annualBondPassiveIncome = activeLots.reduce((total, lot) => total + lot.amount * lot.netAnnualCouponRate, 0);
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
function summarizeProjection(projection, assumptions) {
    const final = projection.at(-1);
    const firstMonthAt = (amount) => projection.find((row) => row.totalAccountValue >= amount)?.month ?? null;
    const crossoverMonth = projection.find((row) => row.annualPassiveIncome > row.personalContribution * 12)?.month ?? null;
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
        milestone50m: firstMonthAt(50000000),
        milestone100m: firstMonthAt(100000000),
        milestone200m: firstMonthAt(200000000),
        passiveIncomeCrossoverYear: crossoverMonth
            ? Math.ceil(crossoverMonth / 12)
            : null,
    };
}
function formatRwf(value, compact = false) {
    if (compact && Math.abs(value) >= 1000000000 && Math.abs(value) < 1000000000000) {
        const billions = Math.trunc((value / 1000000000) * 100) / 100;
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
function formatPercent(value, maximumFractionDigits = 1) {
    return new Intl.NumberFormat("en", {
        style: "percent",
        maximumFractionDigits,
    }).format(value);
}

},
"./pdf-import-parser": function(require,module,exports) {
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.isRecognizedBondResult = isRecognizedBondResult;
exports.parseBondPdfText = parseBondPdfText;
const MONTHS = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"];
const DATE_PATTERN = /([\dIlioO]{1,2})\s*[-/.]?\s*(Jan|Feb|Mar|Apr|May|Jun|Ju[lI]|Aug|Sep|Oct|Nov|Dec)[a-z]*\s*[-/.]?\s*([\dIlioO]{2,4})(?!\d)/gi;
const digits = (value) => value.replace(/[Ili]/g, "1").replace(/[oO]/g, "0");
function isRecognizedBondResult(result, text) {
    const fields = result.fields;
    const identity = Boolean(fields.bondName || fields.isin);
    const terms = [fields.tenorYears, fields.couponRate, fields.settlementDate, fields.maturityDate].filter((value) => value !== undefined && value !== "").length;
    const context = fields.issuer === "Government of Rwanda" || /INVESTOR\s+RESULTS|RESULTS\s+OF[\s\S]{0,80}TREASURY\s+BOND/i.test(text);
    return identity && terms >= 2 && context;
}
function isoDate(year, month, day) {
    if (year < 2000 || year > 2199 || month < 1 || month > 12 || day < 1 || day > 31)
        return null;
    const date = new Date(Date.UTC(year, month - 1, day));
    return date.getUTCMonth() === month - 1 && date.getUTCDate() === day
        ? date.toISOString().slice(0, 10)
        : null;
}
function readDate(value) {
    const numeric = value.match(/\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})\b/);
    if (numeric)
        return isoDate(+numeric[3], +numeric[2], +numeric[1]);
    const match = [...value.matchAll(DATE_PATTERN)][0];
    if (!match)
        return null;
    const year = +digits(match[3]);
    return isoDate(year < 100 ? 2000 + year : year, MONTHS.indexOf(match[2].toLowerCase().slice(0, 3).replace(/^jui$/, "jul")) + 1, +digits(match[1]));
}
function number(value) {
    if (!value)
        return undefined;
    const result = Number(value.replace(/[,\s]/g, ""));
    return Number.isFinite(result) ? result : undefined;
}
/** BNR result keywords only. Text is data; nothing in a document is executed. */
function parseBondPdfText(raw, usedOcr = false) {
    const text = raw.replace(/\r/g, "").replace(/[|_]/g, " ");
    const fields = {};
    const warnings = [];
    const capture = (pattern) => text.match(pattern)?.[1]?.trim();
    const name = capture(/(?:ISSUANCE\s+NUMBER|ISSUE\s+NUMBER)\s*[:\s]*([A-Z]+\d+\s*\/\s*\d{4}\s*\/\s*\d+\s*(?:YR|Y))/i);
    if (name)
        fields.bondName = name.replace(/\s/g, "").toUpperCase();
    const code = capture(/(?:BOND\s+CODE|ISIN(?:\s*\/\s*SECURITY\s+CODE)?)\s*[:\s]*([A-Z]{2}[A-Z0-9/.-]{8,30})/i);
    if (code)
        fields.isin = code.toUpperCase();
    if (/NATIONAL BANK OF RWANDA|GOVERNMENT OF RWANDA|BANKI NKURU/i.test(text)) {
        fields.issuer = "Government of Rwanda";
        fields.instrumentType = "treasury";
        fields.currency = "RWF";
        fields.market = "primary";
    }
    const tenor = number(capture(/TENOR\s*[:\s]*(\d+)\s*Y/i) ?? capture(/RESULTS\s+OF\s+(\d+)\s+YEAR/i));
    if (tenor && tenor <= 100)
        fields.tenorYears = tenor;
    const price = number(capture(/(?:^|\n)\s*(?:EXECUTED\s+)?PRICE\s*[:\s]*(\d+(?:\.\d+)?)/i));
    if (price && price <= 1000)
        fields.pricePercent = price;
    const rate = number(capture(/C[ao]upon\s+(?:Interest\s+)?rate\s*[:\s]*(\d+(?:\.\d+)?)\s*%/i));
    if (rate !== undefined && rate <= 100)
        fields.couponRate = rate / 100;
    const settlement = capture(/SETTLEMENT\s+DATE\s*[:\s]*([^\n]+(?:\n[^\n]+)?)/i);
    const maturity = capture(/REDEMP?E?TION\s+DATE\s*[:\s]*([^\n]+(?:\n[^\n]+)?)/i)
        ?? capture(/MATURITY\s+DATE\s*[:\s]*([^\n]+(?:\n[^\n]+)?)/i);
    if (settlement) {
        const date = readDate(settlement);
        if (date) {
            fields.settlementDate = date;
            fields.purchaseDate = date;
            warnings.push("Trade date defaults to settlement date. Change it if your transaction used a different date.");
        }
    }
    if (maturity) {
        const date = readDate(maturity);
        if (date)
            fields.maturityDate = date;
    }
    // AMOUNT near the bond header is the whole issuance, never this investor's principal.
    // In the BNR investor table the columns are applied, allocated, settlement, coupon.
    const investorSection = text.split(/INVESTOR\s+RESULTS/i)[1]?.split(/Digitally\s+signed/i)[0];
    if (investorSection && /Allocation/i.test(investorSection) && /Coupon\s+Am[o0]unt/i.test(investorSection)) {
        const amounts = [...investorSection.matchAll(/\b\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?\b/g)].map((match) => number(match[0]));
        if (amounts.length === 4 && amounts[1] > 0) {
            fields.faceValue = amounts[1];
            if (fields.pricePercent) {
                const expected = Math.round(amounts[1] * fields.pricePercent) / 100;
                if (Math.abs(expected - amounts[2]) > 1) {
                    warnings.push("Settlement amount differs from principal × price. Enter any accrued interest and fees before saving.");
                }
            }
        }
        else {
            warnings.push("Could not identify one investor allocation unambiguously. Enter your own face value; the total issuance amount was ignored.");
        }
    }
    if (/BK\s+CAPITAL/i.test(investorSection ?? ""))
        fields.broker = "BK Capital";
    const schedule = text.match(/COUPON\s+PAYMENT(?:\s+DATES)?([\s\S]*?)(?=SETTLEMENT\s+DATE|REDEMP?TION\s+DATE|MATURITY\s+DATE|INVESTOR\s+RESULTS|$)/i)?.[1];
    let invalidDates = 0;
    if (schedule) {
        const dates = [...schedule.matchAll(DATE_PATTERN)].flatMap((match) => {
            const date = readDate(match[0]);
            if (!date)
                invalidDates++;
            return date ? [date] : [];
        });
        const unique = [...new Set(dates)].sort();
        if (unique.length !== dates.length)
            invalidDates++;
        if (unique.length) {
            const filtered = unique.filter((date) => (!fields.settlementDate || date > fields.settlementDate) && (!fields.maturityDate || date <= fields.maturityDate));
            invalidDates += unique.length - filtered.length;
            fields.couponDates = filtered;
            fields.firstCouponDate = filtered[0] ?? "";
            fields.couponFrequency = 2;
            // OCR results always require review. Missing dates are never filled by guessing.
            fields.scheduleConfidence = !usedOcr && !invalidDates && fields.tenorYears && filtered.length === fields.tenorYears * 2 ? "confirmed" : "estimated";
            if (fields.tenorYears && filtered.length !== fields.tenorYears * 2) {
                warnings.push(`Found ${filtered.length} coupon dates; a full ${fields.tenorYears}-year semiannual schedule usually has ${fields.tenorYears * 2}. Check for missing dates or a reopened bond.`);
            }
        }
    }
    if (invalidDates)
        warnings.push("Some coupon dates were invalid, duplicated, or outside the bond term. Check the schedule against your PDF.");
    if (usedOcr)
        warnings.push("This PDF needed text recognition. Check every value, especially the security code, principal, and coupon dates, against the original.");
    const required = [
        ["bondName", "Bond name"], ["isin", "Security code"], ["tenorYears", "Tenor"],
        ["faceValue", "Your allocated principal"], ["pricePercent", "Executed price"],
        ["couponRate", "Coupon rate"], ["settlementDate", "Settlement date"],
        ["maturityDate", "Maturity date"], ["firstCouponDate", "Coupon schedule"],
    ];
    const missingFields = required.filter(([key]) => fields[key] === undefined || fields[key] === "").map(([, label]) => label);
    return { fields, warnings, missingFields, usedOcr };
}

},
"./equity-pdf-parser": function(require,module,exports) {
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.EMPTY_EQUITY = void 0;
exports.parseEquityPdfText = parseEquityPdfText;
exports.parsePurchasePdfText = parsePurchasePdfText;
const equity_validation_1 = require("./equity-validation");
const pdf_import_parser_1 = require("./pdf-import-parser");
const pdf_import_errors_1 = require("./pdf-import-errors");
exports.EMPTY_EQUITY = {
    instrumentType: "equity", exchange: "RSE", securityName: "", ticker: "", isin: "", tradeDate: "", settlementDate: "",
    shares: 0, pricePerShare: 0, grossConsideration: 0, brokerageCommission: 0, rseTransactionFee: 0, csdTransactionLevy: 0,
    cmaTransactionFee: 0, totalCharges: 0, netAmountPayable: 0, broker: "", contractNote: "", cdsAccountNo: "", cdsReference: "", tradeReportId: "", currency: "RWF", status: "active", notes: "",
};
function date(text) {
    const match = text.match(/(\d{1,2})[-/\s](Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/\s](\d{4})/i);
    if (!match)
        return "";
    const month = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"].indexOf(match[2].toLowerCase().slice(0, 3)) + 1;
    const value = `${match[3]}-${String(month).padStart(2, "0")}-${match[1].padStart(2, "0")}`;
    return (0, equity_validation_1.validEquityDate)(value) ? value : "";
}
function parseEquityPdfText(raw, usedOcr = false) {
    const text = raw.replace(/\r/g, "");
    if ([...text.matchAll(/EQUITY\s+PURCHASE\s+CONTRACT\s+NOTE\s*:/gi)].length !== 1)
        return null;
    // Sale notes and unrelated PDFs must never become a purchase.
    if (!/EQUITY\s+PURCHASE\s+CONTRACT\s+NOTE\s*:/i.test(text) || /EQUITY\s+SALE\s+CONTRACT/i.test(text))
        return null;
    const capture = (pattern) => text.match(pattern)?.[1]?.trim() ?? "";
    const fields = { ...exports.EMPTY_EQUITY };
    fields.contractNote = capture(/EQUITY\s+PURCHASE\s+CONTRACT\s+NOTE\s*:\s*([A-Z0-9#/-]+)/i);
    fields.securityName = capture(/Security\s+Name\s*:\s*([^\n]+?)(?=\s+(?:Trade|Settlement|ISIN)\s|\n|$)/i);
    fields.isin = capture(/ISIN\s+Code\s*:\s*([A-Z0-9]{8,32})/i).toUpperCase();
    fields.tradeDate = date(capture(/Trade\s+Date\s*:\s*([^\n]+)/i));
    fields.settlementDate = date(capture(/Settlement\s+Date\s*:\s*([^\n]+)/i));
    fields.shares = Number(capture(/No\.?(?:\s+of)\s+Shares\s*:\s*([\d,]+)/i).replace(/,/g, ""));
    fields.pricePerShare = Number(capture(/\bPrice\s*:\s*([\d,]+(?:\.\d+)?)/i).replace(/,/g, ""));
    fields.broker = /BK\s+CAPITAL/i.test(text) ? "BK Capital" : "";
    fields.cdsAccountNo = capture(/CDS\s+Account\s+No\.\s*:\s*([A-Z0-9]+)/i);
    fields.cdsReference = capture(/CDS\s+Ref\.\s+No\.\s*:\s*([A-Z0-9]+)/i);
    fields.tradeReportId = capture(/Trade\s+Report\s+ID\s*:\s*(\d+)/i);
    const tickers = { RW000A1JCYA: "BOK", RW000A2DN989: "IMR" };
    fields.ticker = tickers[fields.isin] ?? "";
    // Each fee row may contain a percentage and an amount: take its final amount.
    const amount = (label) => {
        const row = text.match(new RegExp(`${label}\\s+([^\\n]+)`, "i"))?.[1];
        if (!row)
            return undefined;
        const values = [...row.matchAll(/\d[\d,]*\.\d{2}(?!\d|%)/g)];
        return values.length ? Number(values.at(-1)[0].replace(/,/g, "")) : undefined;
    };
    const labels = { grossConsideration: "Gross\\s+Consideration", brokerageCommission: "Brokerage\\s+Commission", rseTransactionFee: "RSE\\s+Transaction\\s+Fee", csdTransactionLevy: "CSD\\s+Transaction\\s+Levy", cmaTransactionFee: "CMA\\s+Transaction\\s+Fee", totalCharges: "Total\\s+Charges", netAmountPayable: "NET\\s+AMOUNT\\s+PAYABLE" };
    const missingFields = [];
    for (const [key, label] of Object.entries(labels)) {
        const value = amount(label);
        if (value === undefined)
            missingFields.push(key.replace(/[A-Z]/g, (letter) => ` ${letter.toLowerCase()}`));
        else
            fields[key] = value;
    }
    for (const [label, value] of [["security name", fields.securityName], ["ISIN", fields.isin], ["ticker", fields.ticker], ["trade date", fields.tradeDate], ["settlement date", fields.settlementDate], ["number of shares", fields.shares], ["share price", fields.pricePerShare]])
        if (!value)
            missingFields.push(label);
    // This parser currently understands RSE purchase notes denominated in RWF only.
    if (!/All\s+figures\s+are\s+in\s+RWF/i.test(text) || !/Exchange\s*:\s*RSE\b/i.test(text))
        return null;
    const warnings = usedOcr ? ["Text recognition was used. Check every number against your PDF."] : [];
    if (!missingFields.length && !(0, equity_validation_1.parseEquityPurchase)(fields))
        warnings.push("The extracted totals or dates do not agree. Correct them before saving.");
    return { kind: "equity", fields, missingFields, warnings, usedOcr };
}
function parsePurchasePdfText(text, usedOcr = false) {
    const equity = parseEquityPdfText(text, usedOcr);
    const bond = (0, pdf_import_parser_1.parseBondPdfText)(text, usedOcr);
    const isBond = (0, pdf_import_parser_1.isRecognizedBondResult)(bond, text);
    if (equity && !isBond)
        return equity;
    if (isBond && !equity)
        return { kind: "bond", ...bond };
    throw new pdf_import_errors_1.BondPdfImportError("not_bond_result");
}

},
"./equity-validation": function(require,module,exports) {
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.validEquityDate = validEquityDate;
exports.parseEquityPurchase = parseEquityPurchase;
function validEquityDate(value) {
    return typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value)
        && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;
}
function parseEquityPurchase(value) {
    if (!value || typeof value !== "object")
        return null;
    const raw = value;
    const text = (key, max, required = false) => {
        const value = raw[key] ?? "";
        return typeof value === "string" && value.trim().length <= max && (!required || value.trim()) ? value.trim() : null;
    };
    const securityName = text("securityName", 160, true), isin = text("isin", 32, true), ticker = text("ticker", 10, true);
    const broker = text("broker", 120), contractNote = text("contractNote", 120), cdsAccountNo = text("cdsAccountNo", 120), cdsReference = text("cdsReference", 120), tradeReportId = text("tradeReportId", 120);
    if (!securityName || !isin || !ticker || !/^[A-Z][A-Z0-9]{1,9}$/.test(ticker) || !/^[A-Z0-9]{8,32}$/.test(isin)
        || [broker, contractNote, cdsAccountNo, cdsReference, tradeReportId].includes(null)
        || raw.exchange !== "RSE" || raw.currency !== "RWF" || raw.instrumentType !== "equity"
        || !validEquityDate(raw.tradeDate) || !validEquityDate(raw.settlementDate) || raw.settlementDate < raw.tradeDate)
        return null;
    const numericKeys = ["shares", "pricePerShare", "grossConsideration", "brokerageCommission", "rseTransactionFee", "csdTransactionLevy", "cmaTransactionFee", "totalCharges", "netAmountPayable"];
    const numbers = {};
    for (const key of numericKeys) {
        const number = raw[key];
        if (typeof number !== "number" || !Number.isFinite(number) || number < 0 || number > 1e13)
            return null;
        numbers[key] = number;
    }
    const rounded = (n) => Math.round(n * 100) / 100;
    if (!Number.isSafeInteger(numbers.shares) || numbers.shares <= 0 || numbers.pricePerShare <= 0
        || Math.abs(rounded(numbers.shares * numbers.pricePerShare) - numbers.grossConsideration) > 0.02
        || Math.abs(rounded(numbers.brokerageCommission + numbers.rseTransactionFee + numbers.csdTransactionLevy + numbers.cmaTransactionFee) - numbers.totalCharges) > 0.02
        || Math.abs(rounded(numbers.grossConsideration + numbers.totalCharges) - numbers.netAmountPayable) > 0.02)
        return null;
    return { instrumentType: "equity", securityName, isin, ticker, exchange: "RSE", currency: "RWF", tradeDate: raw.tradeDate, settlementDate: raw.settlementDate,
        ...numbers, broker: broker, contractNote: contractNote, cdsAccountNo: cdsAccountNo, cdsReference: cdsReference, tradeReportId: tradeReportId, status: "active", notes: "" };
}

},
"./pdf-import-errors": function(require,module,exports) {
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.BondPdfImportError = exports.PDF_FILE_ERROR_MESSAGE = exports.MAX_BOND_PDF_BYTES = void 0;
exports.bondPdfImportErrorMessage = bondPdfImportErrorMessage;
exports.validateBondPdfMetadata = validateBondPdfMetadata;
exports.validateBondPdfEnvelope = validateBondPdfEnvelope;
exports.hasPdfEntries = hasPdfEntries;
exports.MAX_BOND_PDF_BYTES = 10 * 1024 * 1024;
exports.PDF_FILE_ERROR_MESSAGE = "❌Wrong file.";
const MESSAGES = {
    file_type: exports.PDF_FILE_ERROR_MESSAGE,
    file_size: exports.PDF_FILE_ERROR_MESSAGE,
    invalid_pdf: exports.PDF_FILE_ERROR_MESSAGE,
    unreadable: exports.PDF_FILE_ERROR_MESSAGE,
    password: exports.PDF_FILE_ERROR_MESSAGE,
    unsafe_content: exports.PDF_FILE_ERROR_MESSAGE,
    page_limit: exports.PDF_FILE_ERROR_MESSAGE,
    scan_limit: exports.PDF_FILE_ERROR_MESSAGE,
    text_limit: exports.PDF_FILE_ERROR_MESSAGE,
    not_bond_result: exports.PDF_FILE_ERROR_MESSAGE,
    cancelled: exports.PDF_FILE_ERROR_MESSAGE,
};
class BondPdfImportError extends Error {
    constructor(code, diagnostic) {
        super(MESSAGES[code]);
        this.code = code;
        this.diagnostic = diagnostic;
        this.name = "BondPdfImportError";
    }
}
exports.BondPdfImportError = BondPdfImportError;
// Display only our fixed messages, never dependency errors or document content.
function bondPdfImportErrorMessage(error) {
    return error instanceof BondPdfImportError && Object.hasOwn(MESSAGES, error.code)
        ? MESSAGES[error.code] : MESSAGES.unreadable;
}
function validateBondPdfMetadata(file) {
    // Generic provider metadata is not a content verdict. The bytes and parsed
    // structure are still checked before extracting anything from the document.
    if (!/\.pdf$/i.test(file.name) || !["", "application/pdf", "application/octet-stream"].includes(file.type.toLowerCase()))
        throw new BondPdfImportError("file_type");
    if (!file.size || file.size > exports.MAX_BOND_PDF_BYTES)
        throw new BondPdfImportError("file_size");
}
function validateBondPdfEnvelope(bytes) {
    const decode = new TextDecoder();
    // Some broker exports reserve a large zero-filled buffer after %%EOF.
    // Strip only PDF whitespace/NUL padding, never arbitrary trailing payloads.
    let end = bytes.length;
    while (end > 0 && [0, 9, 10, 12, 13, 32].includes(bytes[end - 1]))
        end--;
    if (bytes.length > exports.MAX_BOND_PDF_BYTES ||
        !/^%PDF-(?:1\.[0-7]|2\.0)[\r\n\t ]/.test(decode.decode(bytes.subarray(0, 16))) ||
        !/%%EOF[\t\n\f\r \0]*$/.test(decode.decode(bytes.subarray(Math.max(0, end - 1024), end)))) {
        throw new BondPdfImportError("invalid_pdf", { stage: "file-envelope", reason: "validation" });
    }
}
function hasPdfEntries(value) {
    return value instanceof Map ? value.size > 0 : Boolean(value && typeof value === "object" && Object.keys(value).length);
}

},
"./bk-statement-parser": function(require,module,exports) {
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.parseBkStatement = parseBkStatement;
const equity_validation_1 = require("./equity-validation");
const pdf_import_errors_1 = require("./pdf-import-errors");
const value = (text) => Number(text.replace(/,/g, ""));
function date(raw) {
    const match = raw.match(/^(\d{1,2})-(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)-(\d{4})$/i);
    if (!match)
        return "";
    const month = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"].indexOf(match[2].toLowerCase()) + 1;
    const result = `${match[3]}-${String(month).padStart(2, "0")}-${match[1].padStart(2, "0")}`;
    return (0, equity_validation_1.validEquityDate)(result) ? result : "";
}
/** A statement is a ledger for reconciliation, not evidence of missing execution terms. */
function parseBkStatement(raw, usedOcr = false) {
    if (!/\bBK\s+CAPITAL\b/i.test(raw) || !/\bCLIENT\s+STATEMENT\b/i.test(raw))
        return null;
    const fail = () => { throw new pdf_import_errors_1.BondPdfImportError("not_bond_result"); };
    if (!/Balance\s*\(RWF\)/i.test(raw) || /\b(?:USD|EUR|GBP)\b/.test(raw))
        return fail();
    const period = (label) => date(raw.match(new RegExp(`${label}\\s+Date\\s*:\\s*(\\d{1,2}-[a-z]{3}-\\d{4})`, "i"))?.[1] ?? "");
    const fromDate = period("From"), toDate = period("To");
    if (!fromDate || !toDate || toDate < fromDate)
        return fail();
    const ledger = raw.split(/Opening\s+Balance\s+As\s+On/i)[1]?.split(/Closing\s+Balance\s+As\s+On/i)[0];
    if (!ledger)
        return fail();
    const starts = [...ledger.matchAll(/^\s*(\d{1,2}-[a-z]{3}-\d{4})\s+(Receipt|Journal|Purchase|Sale|Payment)\b/gim)];
    const rows = [];
    for (let i = 0; i < starts.length; i++) {
        const match = starts[i];
        const block = ledger.slice(match.index, starts[i + 1]?.index);
        const text = block.replace(/\s+/g, " ").trim();
        const day = date(match[1]);
        if (!day || day < fromDate || day > toDate)
            return fail();
        // Receipts, fees, sales and transfers never become investment purchases.
        if (match[2].toLowerCase() !== "purchase" && !(match[2].toLowerCase() === "journal" && /\bBond\s+Cost\b/i.test(text)))
            continue;
        const amounts = [...text.matchAll(/-?\b\d[\d,]*\.\d{2}\b/g)].map(item => value(item[0]));
        if (amounts.length < 2)
            return fail();
        const amount = amounts[amounts.length - 2], credit = amounts[amounts.length - 1];
        if (!Number.isFinite(amount) || amount <= 0 || amount > 1e13 || credit !== 0)
            return fail();
        if (match[2].toLowerCase() === "purchase") {
            const trade = text.match(/Purchase\s+of\s+([A-Z][A-Z0-9]{1,9})\s+([\d,]+)\s*@\s*([\d,]+\.\d{2})\s+on\s+(\d{2})-(\d{2})-(\d{4})/i);
            const contract = block.match(/\bPurchase\s+(\d+#)(?:[ \t]*(\d+)\b|[^\n]*\n\s*(\d+)\b)/i);
            if (!trade || !contract)
                return fail();
            const shares = value(trade[2]), pricePerShare = value(trade[3]);
            if (!Number.isSafeInteger(shares) || shares <= 0 || pricePerShare <= 0 || `${trade[6]}-${trade[5]}-${trade[4]}` !== day || amount < shares * pricePerShare)
                return fail();
            rows.push({ kind: "equity", date: day, amount, title: trade[1].toUpperCase(), ticker: trade[1].toUpperCase(), shares, pricePerShare, contractNote: contract[1] + (contract[2] ?? contract[3]) });
        }
        else {
            const title = text.match(/\b([A-Za-z]+\s+Bond\s+Cost)\b/i)?.[1] ?? "Bond purchase";
            rows.push({ kind: "bond", date: day, amount, title });
        }
    }
    if (!rows.length || rows.length > 500)
        return fail();
    return { kind: "statement", broker: "BK Capital", currency: "RWF", fromDate, toDate, rows, usedOcr };
}

}}, cache={};
function require(name){if(cache[name])return cache[name].exports;var module={exports:{}};cache[name]=module;modules[name](require,module,module.exports);return module.exports;}
var calc=require('./calculations'), pdf=require('./equity-pdf-parser');
global.PortfolioLogic={
project:function(input){var assumptions=Object.assign({},calc.DEFAULT_ASSUMPTIONS,input.assumptions);var lots=input.includeHoldings?input.purchases.map(function(p){return calc.purchaseToStartingLot(p,assumptions)}).filter(Boolean):undefined;var rows=calc.calculateProjection(assumptions,input.injections||[],lots);return {rows:rows,summary:calc.summarizeProjection(rows,assumptions)};},
parse:function(text,usedOcr){return require('./bk-statement-parser').parseBkStatement(text,usedOcr)||pdf.parsePurchasePdfText(text,usedOcr);}
};
})(globalThis);
