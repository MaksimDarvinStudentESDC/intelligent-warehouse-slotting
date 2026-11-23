package com.warehouse.slotting.controller;

import com.warehouse.slotting.dto.LocationResponse;
import com.warehouse.slotting.service.LocationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/warehouses/{warehouseId}/locations")
public class LocationController {
    private final LocationService locations;

    public LocationController(LocationService locations) {
        this.locations = locations;
    }

    @GetMapping
    public List<LocationResponse> list(@PathVariable Long warehouseId) {
        return locations.listActiveLocations(warehouseId);
    }
}
