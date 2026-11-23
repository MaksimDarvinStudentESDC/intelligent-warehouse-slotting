package com.warehouse.slotting.controller;

import com.warehouse.slotting.dto.RecommendationResponse;
import com.warehouse.slotting.service.RecommendationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService service;
    public RecommendationController(RecommendationService service) { this.service = service; }

    @GetMapping("/batches/{batchId}")
    public RecommendationResponse recommend(@PathVariable Long batchId,
            @RequestParam(defaultValue = "5") int topN,
            @RequestParam(defaultValue = "nearest_feasible") String strategy) {
        return service.recommend(batchId, topN, strategy);
    }
}
