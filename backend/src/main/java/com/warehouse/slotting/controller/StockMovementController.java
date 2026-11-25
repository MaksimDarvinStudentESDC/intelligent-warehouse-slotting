package com.warehouse.slotting.controller;
import com.warehouse.slotting.dto.StockMovementResponse;
import com.warehouse.slotting.service.StockMovementService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
@RestController
@RequestMapping("/api/warehouses/{warehouseId}/movements")
public class StockMovementController {
    private final StockMovementService service;
    public StockMovementController(StockMovementService service) { this.service = service; }
    public record HistoryPage(java.util.List<StockMovementResponse> content, int page, int size, long totalElements, int totalPages) {}
    @GetMapping
    public HistoryPage history(@PathVariable Long warehouseId,
            @RequestParam(required=false) Long productId, @RequestParam(required=false) Long locationId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        Page<StockMovementResponse> result = service.history(warehouseId, productId, locationId, from, to, page, size);
        return new HistoryPage(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
