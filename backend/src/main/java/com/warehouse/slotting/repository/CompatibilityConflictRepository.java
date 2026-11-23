package com.warehouse.slotting.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CompatibilityConflictRepository {
    private final JdbcTemplate jdbc;

    public CompatibilityConflictRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasConflict(Long locationId, Long groupId) {
        Integer conflicts = jdbc.queryForObject("""
                SELECT COUNT(*) FROM batches existing
                JOIN products p ON p.id = existing.product_id
                JOIN compat_conflicts cc ON cc.warehouse_id = existing.warehouse_id
                WHERE existing.current_location_id = ?
                  AND existing.status = 'placed'
                  AND ((cc.group_a_id = ? AND cc.group_b_id = p.compat_group_id)
                    OR (cc.group_b_id = ? AND cc.group_a_id = p.compat_group_id))
                """, Integer.class, locationId, groupId, groupId);
        return conflicts != null && conflicts > 0;
    }
}
