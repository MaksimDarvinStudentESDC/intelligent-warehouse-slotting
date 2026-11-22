package com.warehouse.slotting.controller;

import com.warehouse.slotting.service.ClassificationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/classifications")
public class ClassificationController {

    private final ClassificationService service;

    public ClassificationController(ClassificationService service) {
        this.service = service;
    }

    public record RunClassificationRequest(
            Long warehouseId,
            LocalDate windowStart,
            LocalDate windowEnd
    ) { }

    @PostMapping("/run")
    @ResponseStatus(HttpStatus.CREATED)
    public ClassificationService.ClassificationResult run(
            @RequestBody RunClassificationRequest request) {
        return service.run(request.warehouseId(), request.windowStart(), request.windowEnd());
    }
}
