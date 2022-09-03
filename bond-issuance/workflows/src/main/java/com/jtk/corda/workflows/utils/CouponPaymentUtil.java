package com.jtk.corda.workflows.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class CouponPaymentUtil {
    private static final Logger log = LoggerFactory.getLogger(CouponPaymentUtil.class);

    public static long getCouponPayments(int paymentFrequency,
                                        int daysPerMonth,
                                        LocalDate maturityDate,
                                        LocalDate issueDate){
        long daysInBetween = ChronoUnit.DAYS.between(issueDate, maturityDate);
        long months = daysInBetween / daysPerMonth;
        long numberOfPayments = (months / paymentFrequency);
        log.info("{} Days per month, Total months: {}, Coupon Payment Left {}",daysPerMonth, months, numberOfPayments);
        return numberOfPayments;
    }

    public static LocalDate getNextCouponPaymentDate(LocalDate fromDate, int daysPerMonth, int paymentFrequency){
        return fromDate.plusDays(daysPerMonth * paymentFrequency);
    }

}
