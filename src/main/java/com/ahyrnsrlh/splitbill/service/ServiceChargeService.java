package com.ahyrnsrlh.splitbill.service;

import com.ahyrnsrlh.splitbill.config.AppProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Computes the service charge percentage from the configured GitHub username.
 *
 * Algorithm: serviceChargePct = sumOfUnicodeCodePoints(lowercase(username)) % 10
 *
 * For "ahyrnsrlh": sum = 97+104+121+114+110+115+114+108+104 = 987, 987 % 10 = 7
 */
@Service
public class ServiceChargeService {

    private final int serviceChargePct;

    public ServiceChargeService(AppProperties appProperties) {
        this.serviceChargePct = calculatePct(appProperties.githubUsername());
    }

    private static int calculatePct(String username) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        int sum = username.toLowerCase().chars().sum();
        return sum % 10;
    }

    public int getServiceChargePct() {
        return serviceChargePct;
    }

    /**
     * Calculates the informational service charge amount.
     * Uses scale=2 and HALF_UP rounding.
     * The service charge is NOT added to participant balances; it is display-only.
     */
    public BigDecimal calculateServiceChargeAmount(BigDecimal totalExpenses) {
        return totalExpenses
                .multiply(BigDecimal.valueOf(serviceChargePct))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
