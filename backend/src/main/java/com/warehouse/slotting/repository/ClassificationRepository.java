package com.warehouse.slotting.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.function.Consumer;

@Repository
public class ClassificationRepository {
    private final JdbcTemplate jdbc;

    public ClassificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record OutboundMovement(long productId, LocalDate day, int quantity, int picksCount) {}

    public void forEachOutboundMovement(Long warehouseId, LocalDate start, LocalDate end,
                                        Consumer<OutboundMovement> consumer) {
        jdbc.query("""
                SELECT h.product_id, h.event_date, h.quantity, h.picks_count
                FROM outbound_history h
                JOIN products p ON p.id = h.product_id
                WHERE p.warehouse_id = ? AND p.is_archived = false
                  AND h.event_date BETWEEN ? AND ?
                """, rs -> {
            consumer.accept(new OutboundMovement(rs.getLong("product_id"),
                    rs.getDate("event_date").toLocalDate(), rs.getInt("quantity"),
                    rs.getInt("picks_count")));
        }, warehouseId, Date.valueOf(start), Date.valueOf(end));
    }

    public Long createRun(Long warehouseId, LocalDate start, LocalDate end,
                          double aShare, double bShare, double xMax, double yMax) {
        return jdbc.queryForObject("""
                INSERT INTO classification_runs
                    (warehouse_id, window_start, window_end,
                     abc_a_share, abc_b_share, cv_x_max, cv_y_max)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, warehouseId, Date.valueOf(start), Date.valueOf(end),
                BigDecimal.valueOf(aShare), BigDecimal.valueOf(bShare),
                BigDecimal.valueOf(xMax), BigDecimal.valueOf(yMax));
    }

    public void saveProductClasses(Long runId, Long warehouseId, Long productId, String abc,
                                   String xyz, int movementCount, BigDecimal cumulativeShare,
                                   BigDecimal cv, String note) {
        jdbc.update("""
                INSERT INTO product_classes
                  (classification_run_id, warehouse_id, product_id, abc_class,
                   xyz_class, movement_count, cumulative_share, cv, note)
                VALUES (?, ?, ?, ?::abc_class, ?::xyz_class, ?, ?, ?, ?)
                """, runId, warehouseId, productId, abc, xyz, movementCount,
                cumulativeShare, cv, note);
    }
}
