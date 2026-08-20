package com.ahyrnsrlh.splitbill.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record SettlementResponse(
        Long groupId,
        String groupName,
        BigDecimal totalExpenses,

        /**
         * service_charge_pct is required to appear with this exact snake_case key per the spec.
         */
        @JsonProperty("service_charge_pct")
        int serviceChargePct,

        /**
         * service_charge_amount is required to appear with this exact snake_case key per the spec.
         * This is informational only and does not alter individual participant balances.
         */
        @JsonProperty("service_charge_amount")
        BigDecimal serviceChargeAmount,

        List<TransferResponse> settlements
) {}
