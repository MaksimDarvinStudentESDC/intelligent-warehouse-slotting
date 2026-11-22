package com.warehouse.slotting.service;

import com.warehouse.slotting.entity.Product;
import com.warehouse.slotting.entity.Warehouse;
import com.warehouse.slotting.entity.WarehouseKind;
import com.warehouse.slotting.repository.ProductRepository;
import com.warehouse.slotting.repository.WarehouseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClassificationService {
    private static final double A_SHARE = 0.80;
    private static final double B_SHARE = 0.15;
    private static final double X_MAX = 0.50;
    private static final double Y_MAX = 1.00;

    private final JdbcTemplate jdbc;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final ClassificationCalculator calculator = new ClassificationCalculator();

    public ClassificationService(JdbcTemplate jdbc, ProductRepository products,
                                 WarehouseRepository warehouses) {
        this.jdbc = jdbc;
        this.products = products;
        this.warehouses = warehouses;
    }

    public record ProductClassification(Long productId, String sku, String abcClass,
                                        String xyzClass, long movementCount,
                                        BigDecimal cumulativeShare, BigDecimal cv, String note) { }

    public record ClassificationResult(Long runId, Long warehouseId, LocalDate windowStart,
                                       LocalDate windowEnd, List<ProductClassification> products) { }

    /** Creates one persisted classification run for a LIVE warehouse. */
    @Transactional
    public ClassificationResult run(Long warehouseId, LocalDate start, LocalDate end) {
        if (warehouseId == null || start == null || end == null || start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid warehouse or date range");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) > 3650) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Window must not exceed 3651 days");
        }
        Warehouse warehouse = warehouses.findById(warehouseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Warehouse not found"));
        if (warehouse.getKind() != WarehouseKind.LIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Persisted classification runs are supported only for LIVE warehouses");
        }

        List<Product> warehouseProducts = new ArrayList<>(
                products.findByWarehouseIdAndArchivedFalse(warehouseId));
        Map<Long, Map<LocalDate, Integer>> quantities = new HashMap<>();
        Map<Long, Long> picks = new HashMap<>();
        jdbc.query("""
                SELECT h.product_id, h.event_date, h.quantity, h.picks_count
                FROM outbound_history h
                JOIN products p ON p.id = h.product_id
                WHERE p.warehouse_id = ? AND p.is_archived = false
                  AND h.event_date BETWEEN ? AND ?
                """, rs -> {
            long productId = rs.getLong("product_id");
            LocalDate day = rs.getDate("event_date").toLocalDate();
            quantities.computeIfAbsent(productId, ignored -> new HashMap<>())
                    .merge(day, rs.getInt("quantity"), Integer::sum);
            picks.merge(productId, (long) rs.getInt("picks_count"), Long::sum);
        }, warehouseId, Date.valueOf(start), Date.valueOf(end));

        warehouseProducts.sort(Comparator
                .comparingLong((Product p) -> picks.getOrDefault(p.getId(), 0L)).reversed()
                .thenComparing(Product::getId));
        long totalPicks = warehouseProducts.stream()
                .mapToLong(p -> picks.getOrDefault(p.getId(), 0L)).sum();

        Long runId = jdbc.queryForObject("""
                INSERT INTO classification_runs
                    (warehouse_id, window_start, window_end,
                     abc_a_share, abc_b_share, cv_x_max, cv_y_max)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, warehouseId, Date.valueOf(start), Date.valueOf(end),
                BigDecimal.valueOf(A_SHARE), BigDecimal.valueOf(B_SHARE),
                BigDecimal.valueOf(X_MAX), BigDecimal.valueOf(Y_MAX));

        List<ProductClassification> results = new ArrayList<>();
        long cumulativePicks = 0;
        for (Product product : warehouseProducts) {
            long productPicks = picks.getOrDefault(product.getId(), 0L);
            ClassificationCalculator.AbcClass abc = calculator.calculateAbc(
                    cumulativePicks, totalPicks, A_SHARE, B_SHARE);
            cumulativePicks += productPicks;

            List<Integer> daily = new ArrayList<>();
            Map<LocalDate, Integer> byDay = quantities.getOrDefault(product.getId(), Map.of());
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                daily.add(byDay.getOrDefault(date, 0));
            }
            Double cvValue = calculator.calculateCv(daily);
            ClassificationCalculator.XyzClass xyz = calculator.calculateXyz(cvValue, X_MAX, Y_MAX);
            BigDecimal cumulativeShare = totalPicks == 0 ? null
                    : BigDecimal.valueOf((double) cumulativePicks / totalPicks)
                    .setScale(5, RoundingMode.HALF_UP);
            BigDecimal cv = cvValue == null ? null
                    : BigDecimal.valueOf(cvValue).setScale(4, RoundingMode.HALF_UP);
            String note = cvValue == null ? "no data" : null;

            jdbc.update("""
                    INSERT INTO product_classes
                      (classification_run_id, warehouse_id, product_id, abc_class,
                       xyz_class, movement_count, cumulative_share, cv, note)
                    VALUES (?, ?, ?, ?::abc_class, ?::xyz_class, ?, ?, ?, ?)
                    """, runId, warehouseId, product.getId(), abc.name(),
                    xyz == null ? null : xyz.name(), Math.toIntExact(productPicks),
                    cumulativeShare, cv, note);
            results.add(new ProductClassification(product.getId(), product.getSku(),
                    abc.name(), xyz == null ? null : xyz.name(), productPicks,
                    cumulativeShare, cv, note));
        }
        return new ClassificationResult(runId, warehouseId, start, end, List.copyOf(results));
    }
}
