package com.warehouse.slotting.controller;

import com.warehouse.slotting.dto.ConstraintCheckResponse;
import com.warehouse.slotting.service.HardConstraintsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/constraints")
public class HardConstraintsController {
    private final HardConstraintsService service;

    public HardConstraintsController(HardConstraintsService service) {
        this.service = service;
    }

    @GetMapping("/batches/{batchId}/locations/{locationId}")
    public ConstraintCheckResponse check(@PathVariable Long batchId, @PathVariable Long locationId) {
        return service.check(batchId, locationId);
    }
}
