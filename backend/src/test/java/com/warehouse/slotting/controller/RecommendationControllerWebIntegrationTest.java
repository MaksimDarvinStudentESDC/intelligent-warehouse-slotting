package com.warehouse.slotting.controller;

import com.warehouse.slotting.dto.RecommendationItemResponse;
import com.warehouse.slotting.dto.RecommendationResponse;
import com.warehouse.slotting.exception.UseCaseException;
import com.warehouse.slotting.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RecommendationController.class)
class RecommendationControllerWebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    void defaultStrategyReturnsNearestFeasibleJson() throws Exception {
        var item = new RecommendationItemResponse(1, 6L, "ST-06", new BigDecimal("21"), "Nearest feasible location");
        when(recommendationService.recommend(1L, 5, "nearest_feasible"))
                .thenReturn(new RecommendationResponse(1L, "nearest_feasible", 7, List.of(item)));

        mockMvc.perform(get("/api/recommendations/batches/{batchId}", 1L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.batchId").value(1))
                .andExpect(jsonPath("$.strategy").value("nearest_feasible"))
                .andExpect(jsonPath("$.feasibleCount").value(7))
                .andExpect(jsonPath("$.recommendations[0].locationCode").value("ST-06"))
                .andExpect(jsonPath("$.recommendations[0].scoreBreakdown").value(org.hamcrest.Matchers.nullValue()));

        verify(recommendationService).recommend(1L, 5, "nearest_feasible");
    }

    @Test
    void weightedStrategyPassesParametersAndReturnsJson() throws Exception {
        when(recommendationService.recommend(1L, 3, "weighted_score"))
                .thenReturn(new RecommendationResponse(1L, "weighted_score", 7, List.of()));

        mockMvc.perform(get("/api/recommendations/batches/{batchId}", 1L)
                        .param("topN", "3")
                        .param("strategy", "weighted_score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("weighted_score"))
                .andExpect(jsonPath("$.recommendations").isArray());

        verify(recommendationService).recommend(1L, 3, "weighted_score");
    }

    @Test
    void invalidStrategyMapsBusinessExceptionTo400() throws Exception {
        when(recommendationService.recommend(1L, 5, "invalid_strategy"))
                .thenThrow(new UseCaseException(UseCaseException.Kind.INVALID_INPUT, "Unknown strategy"));

        mockMvc.perform(get("/api/recommendations/batches/1")
                        .param("strategy", "invalid_strategy"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingBatchMapsBusinessExceptionTo404() throws Exception {
        when(recommendationService.recommend(999L, 5, "nearest_feasible"))
                .thenThrow(new UseCaseException(UseCaseException.Kind.NOT_FOUND, "Batch not found"));

        mockMvc.perform(get("/api/recommendations/batches/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void conflictingRequestMapsBusinessExceptionTo409() throws Exception {
        when(recommendationService.recommend(1L, 5, "weighted_score"))
                .thenThrow(new UseCaseException(UseCaseException.Kind.CONFLICT, "Conflicting warehouse state"));

        mockMvc.perform(get("/api/recommendations/batches/1")
                        .param("strategy", "weighted_score"))
                .andExpect(status().isConflict());
    }

    @Test
    void nonNumericTopNReturns400() throws Exception {
        mockMvc.perform(get("/api/recommendations/batches/1")
                        .param("topN", "abc"))
                .andExpect(status().isBadRequest());
    }
}
