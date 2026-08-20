package com.ahyrnsrlh.splitbill.service;

import com.ahyrnsrlh.splitbill.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ServiceChargeServiceTest {

    /**
     * Verifies that the service charge percentage is computed from the username at runtime,
     * not hardcoded. For "ahyrnsrlh": 97+104+121+114+110+115+114+108+104 = 987, 987 % 10 = 7.
     */
    @Test
    void serviceChargePctIsCalculatedFromUsername_ahyrnsrlh() {
        AppProperties props = new AppProperties("ahyrnsrlh");
        ServiceChargeService service = new ServiceChargeService(props);

        assertEquals(7, service.getServiceChargePct());
    }

    @Test
    void serviceChargePctIsCalculatedFromUsername_uppercaseIsNormalized() {
        // Same username in uppercase should produce the same result
        AppProperties props = new AppProperties("AHYRNSRLH");
        ServiceChargeService service = new ServiceChargeService(props);

        assertEquals(7, service.getServiceChargePct());
    }

    @Test
    void serviceChargePctChangesWithDifferentUsername() {
        // "abc" = 97+98+99 = 294, 294 % 10 = 4
        AppProperties props = new AppProperties("abc");
        ServiceChargeService service = new ServiceChargeService(props);

        assertEquals(4, service.getServiceChargePct());
    }

    @Test
    void serviceChargeAmountCalculation() {
        AppProperties props = new AppProperties("ahyrnsrlh");
        ServiceChargeService service = new ServiceChargeService(props);

        // 1400000 * 7 / 100 = 98000.00
        BigDecimal result = service.calculateServiceChargeAmount(new BigDecimal("1400000.00"));
        assertEquals(0, result.compareTo(new BigDecimal("98000.00")));
    }

    @Test
    void serviceChargeAmountWithZeroExpenses() {
        AppProperties props = new AppProperties("ahyrnsrlh");
        ServiceChargeService service = new ServiceChargeService(props);

        BigDecimal result = service.calculateServiceChargeAmount(BigDecimal.ZERO);
        assertEquals(0, result.compareTo(BigDecimal.ZERO));
    }
}
