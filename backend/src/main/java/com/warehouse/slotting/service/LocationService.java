package com.warehouse.slotting.service;

import com.warehouse.slotting.dto.LocationResponse;
import com.warehouse.slotting.repository.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LocationService {
    private final LocationRepository locations;

    public LocationService(LocationRepository locations) {
        this.locations = locations;
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> listActiveLocations(Long warehouseId) {
        return locations.findByWarehouseIdAndIsActiveTrue(warehouseId).stream()
                .map(LocationResponse::from)
                .toList();
    }
}
