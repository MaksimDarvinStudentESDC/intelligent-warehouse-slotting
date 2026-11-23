package com.warehouse.slotting.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class LatestProductClassificationRepository {
    private final JdbcTemplate jdbc;

    public LatestProductClassificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProductClasses> findLatest(Long warehouseId, Long productId) {
        return jdbc.query("""
                SELECT pc.abc_class::text, pc.xyz_class::text
                FROM product_classes pc
                JOIN classification_runs cr ON cr.id = pc.classification_run_id
                WHERE cr.warehouse_id = ? AND pc.product_id = ?
                ORDER BY cr.calculated_at DESC, cr.id DESC
                LIMIT 1
                """, (rs, rowNum) -> new ProductClasses(rs.getString(1), rs.getString(2)),
                warehouseId, productId).stream().findFirst();
    }

    public record ProductClasses(String abc, String xyz) {}
}
