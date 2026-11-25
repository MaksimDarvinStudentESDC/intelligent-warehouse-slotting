package com.warehouse.slotting.repository;

import com.warehouse.slotting.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.JoinType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import jakarta.persistence.criteria.Predicate;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long>,
        JpaSpecificationExecutor<StockMovement> {

    default Page<StockMovement> findHistory(Long warehouseId, Long productId,
            Long locationId, OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        java.util.Objects.requireNonNull(warehouseId, "warehouseId");
        Specification<StockMovement> filters = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("warehouse").get("id"), warehouseId));
            if (productId != null) {
                predicates.add(cb.equal(root.get("batch").get("product").get("id"), productId));
            }
            if (locationId != null) {
                var source = root.join("fromLocation", JoinType.LEFT);
                predicates.add(cb.or(cb.equal(source.get("id"), locationId),
                        cb.equal(root.get("toLocation").get("id"), locationId)));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<OffsetDateTime>get("performedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.<OffsetDateTime>get("performedAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return findAll(filters, pageable);
    }
}
