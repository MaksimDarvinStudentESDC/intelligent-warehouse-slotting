package com.warehouse.slotting.controller;

import com.warehouse.slotting.dto.RecommendationResponse;
import com.warehouse.slotting.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @GetMapping("/batches/{batchId}")
    public RecommendationResponse recommend(@PathVariable Long batchId,
                                            @RequestParam(defaultValue = "5") int topN) {
        return service.recommend(batchId, topN);
    }
}
