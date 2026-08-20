package com.ahyrnsrlh.splitbill.controller;

import com.ahyrnsrlh.splitbill.dto.request.CreateGroupRequest;
import com.ahyrnsrlh.splitbill.dto.response.GroupResponse;
import com.ahyrnsrlh.splitbill.dto.response.SettlementResponse;
import com.ahyrnsrlh.splitbill.service.GroupService;
import com.ahyrnsrlh.splitbill.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/groups")
@Tag(name = "Groups", description = "Bill group management")
public class GroupController {

    private final GroupService groupService;
    private final SettlementService settlementService;

    public GroupController(GroupService groupService, SettlementService settlementService) {
        this.groupService = groupService;
        this.settlementService = settlementService;
    }

    @PostMapping
    @Operation(summary = "Create a new bill group with participants")
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.createGroup(request));
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "Get group details and recorded expenses")
    public ResponseEntity<GroupResponse> getGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.getGroup(groupId));
    }

    @GetMapping("/{groupId}/settlements")
    @Operation(summary = "Get settlement summary for a group")
    public ResponseEntity<SettlementResponse> getSettlements(@PathVariable Long groupId) {
        return ResponseEntity.ok(settlementService.calculateSettlement(groupId));
    }
}
