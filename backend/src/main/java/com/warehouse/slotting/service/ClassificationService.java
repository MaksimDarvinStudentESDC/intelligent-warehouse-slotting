package com.warehouse.slotting.service;

import com.warehouse.slotting.exception.UseCaseException;
import com.warehouse.slotting.entity.Product;
import com.warehouse.slotting.entity.Warehouse;
import com.warehouse.slotting.entity.WarehouseKind;
import com.warehouse.slotting.repository.ClassificationRepository;
import com.warehouse.slotting.repository.ProductRepository;
import com.warehouse.slotting.repository.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private final ClassificationRepository classification;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final ClassificationCalculator calculator = new ClassificationCalculator();

    public ClassificationService(ClassificationRepository classification, ProductRepository products,
                                 WarehouseRepository warehouses) {
        this.classification = classification;
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
            throw new UseCaseException(UseCaseException.Kind.INVALID_INPUT, "Invalid warehouse or date range");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) > 3650) {
            throw new UseCaseException(UseCaseException.Kind.INVALID_INPUT, "Window must not exceed 3651 days");
        }
        Warehouse warehouse = warehouses.findById(warehouseId)
                .orElseThrow(() -> new UseCaseException(UseCaseException.Kind.NOT_FOUND, "Warehouse not found"));
        if (warehouse.getKind() != WarehouseKind.LIVE) {
            throw new UseCaseException(UseCaseException.Kind.INVALID_INPUT,
                    "Persisted classification runs are supported only for LIVE warehouses");
        }

        List<Product> warehouseProducts = new ArrayList<>(
                products.findByWarehouseIdAndArchivedFalse(warehouseId));
        Map<Long, Map<LocalDate, Integer>> quantities = new HashMap<>();
        Map<Long, Long> picks = new HashMap<>();
        classification.forEachOutboundMovement(warehouseId, start, end, movement -> {
            quantities.computeIfAbsent(movement.productId(), ignored -> new HashMap<>())
                    .merge(movement.day(), movement.quantity(), Integer::sum);
            picks.merge(movement.productId(), (long) movement.picksCount(), Long::sum);
        });

        warehouseProducts.sort(Comparator
                .comparingLong((Product p) -> picks.getOrDefault(p.getId(), 0L)).reversed()
                .thenComparing(Product::getId));
        long totalPicks = warehouseProducts.stream()
                .mapToLong(p -> picks.getOrDefault(p.getId(), 0L)).sum();

        Long runId = classification.createRun(warehouseId, start, end, A_SHARE, B_SHARE, X_MAX, Y_MAX);

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

            classification.saveProductClasses(runId, warehouseId, product.getId(), abc.name(),
                    xyz == null ? null : xyz.name(), Math.toIntExact(productPicks),
                    cumulativeShare, cv, note);
            results.add(new ProductClassification(product.getId(), product.getSku(),
                    abc.name(), xyz == null ? null : xyz.name(), productPicks,
                    cumulativeShare, cv, note));
        }
        return new ClassificationResult(runId, warehouseId, start, end, List.copyOf(results));
    }
}
