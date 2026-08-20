package com.ahyrnsrlh.splitbill.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class GroupControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createGroup_returns201_withParticipants() throws Exception {
        String body = """
                {
                  "name": "Bali Trip",
                  "participants": [
                    { "name": "Akhyar" },
                    { "name": "Budi" },
                    { "name": "Citra" }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Bali Trip"))
                .andExpect(jsonPath("$.participants", hasSize(3)));
    }

    @Test
    void createGroup_rejectsDuplicateParticipantNames() throws Exception {
        String body = """
                {
                  "name": "Trip",
                  "participants": [
                    { "name": "Akhyar" },
                    { "name": "akhyar" }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Duplicate")));
    }

    @Test
    void getGroup_returns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/api/v1/groups/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void fullFlowEqualSplit_settlementContainsServiceCharge() throws Exception {
        // 1. Create group
        String groupBody = """
                {
                  "name": "Trip",
                  "participants": [
                    { "name": "Alice" },
                    { "name": "Bob" },
                    { "name": "Charlie" }
                  ]
                }
                """;

        MvcResult groupResult = mockMvc.perform(post("/api/v1/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> groupResponse = objectMapper.readValue(
                groupResult.getResponse().getContentAsString(), Map.class);
        int groupId = (int) groupResponse.get("id");
        var participants = (java.util.List<?>) groupResponse.get("participants");
        int aliceId = (int) ((Map<?, ?>) participants.get(0)).get("id");
        int bobId = (int) ((Map<?, ?>) participants.get(1)).get("id");
        int charlieId = (int) ((Map<?, ?>) participants.get(2)).get("id");

        // 2. Add expense: Alice pays 300, equal split
        String expenseBody = String.format("""
                {
                  "description": "Dinner",
                  "amount": 300.00,
                  "paidByParticipantId": %d,
                  "splitType": "EQUAL",
                  "shares": [
                    { "participantId": %d },
                    { "participantId": %d },
                    { "participantId": %d }
                  ]
                }
                """, aliceId, aliceId, bobId, charlieId);

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Dinner"));

        // 3. Get settlements
        mockMvc.perform(get("/api/v1/groups/" + groupId + "/settlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service_charge_pct").value(7))
                .andExpect(jsonPath("$.service_charge_amount").value(21.0))
                .andExpect(jsonPath("$.totalExpenses").value(300.0))
                .andExpect(jsonPath("$.settlements", hasSize(2)));
    }

    @Test
    void createExpense_exactSplitMismatch_returns400() throws Exception {
        // Create group first
        String groupBody = """
                {
                  "name": "Mismatch Test",
                  "participants": [
                    { "name": "A" },
                    { "name": "B" }
                  ]
                }
                """;

        MvcResult groupResult = mockMvc.perform(post("/api/v1/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> groupResponse = objectMapper.readValue(
                groupResult.getResponse().getContentAsString(), Map.class);
        int groupId = (int) groupResponse.get("id");
        var participants = (java.util.List<?>) groupResponse.get("participants");
        int aId = (int) ((Map<?, ?>) participants.get(0)).get("id");
        int bId = (int) ((Map<?, ?>) participants.get(1)).get("id");

        String expenseBody = String.format("""
                {
                  "description": "Hotel",
                  "amount": 600.00,
                  "paidByParticipantId": %d,
                  "splitType": "EXACT",
                  "shares": [
                    { "participantId": %d, "amount": 200.00 },
                    { "participantId": %d, "amount": 100.00 }
                  ]
                }
                """, aId, aId, bId);

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Exact shares must equal")));
    }

    @Test
    void createExpense_invalidSplitType_returns400() throws Exception {
        String groupBody = """
                {
                  "name": "Group",
                  "participants": [{ "name": "A" }]
                }
                """;

        MvcResult groupResult = mockMvc.perform(post("/api/v1/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> groupResponse = objectMapper.readValue(
                groupResult.getResponse().getContentAsString(), Map.class);
        int groupId = (int) groupResponse.get("id");
        var participants = (java.util.List<?>) groupResponse.get("participants");
        int aId = (int) ((Map<?, ?>) participants.get(0)).get("id");

        String expenseBody = String.format("""
                {
                  "description": "Test",
                  "amount": 100.00,
                  "paidByParticipantId": %d,
                  "splitType": "INVALID_TYPE",
                  "shares": [{ "participantId": %d }]
                }
                """, aId, aId);

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unsupported split type")));
    }
}
