package com.seowon.coding.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record PriceAdjustmentPolicy(
        boolean includeTax,
        double percentage,
        double taxRate
) {
    public BigDecimal apply(BigDecimal currentPrice
    ) {

        BigDecimal multiplicand = new BigDecimal(1 + (percentage / 100.0));
        BigDecimal newPrice = currentPrice.multiply(multiplicand);

        if (includeTax) {
            multiplicand = new BigDecimal(1 + (taxRate / 100.0));
            newPrice = newPrice.multiply(multiplicand);
        }
        //원단위로 올림
        return newPrice.setScale(0, RoundingMode.HALF_UP);
    }
}
