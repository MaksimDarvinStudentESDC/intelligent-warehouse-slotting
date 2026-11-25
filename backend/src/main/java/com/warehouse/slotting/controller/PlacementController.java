package com.warehouse.slotting.controller;
import com.warehouse.slotting.dto.*;
import com.warehouse.slotting.service.PlacementService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/placements")
public class PlacementController {
    private final PlacementService service;
    public PlacementController(PlacementService service) { this.service = service; }
    @PostMapping("/confirm")
    public ResponseEntity<PlacementResponse> confirm(@Valid @RequestBody PlacementRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.confirm(request, Long.valueOf(jwt.getSubject())));
    }
}
