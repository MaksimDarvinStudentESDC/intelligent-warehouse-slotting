-- Flyway V3: insert initial reference/configuration data.
-- Source schema: warehouse_slotting_schema_v3_2_1
--
-- Seed only the two fixed baseline strategies used in experiment design:
-- B1 = nearest_feasible
-- B2 = pick_frequency
--
-- The optimized weighted_score configuration is intentionally not seeded here.
-- Its weights and shelf-life threshold must be selected and justified before use.
-- Warehouses, users, products, batches and simulation scenarios are operational
-- or experimental data and are loaded separately.

SET ROLE warehouse_owner;

INSERT INTO strategy_configs (
    name,
    version,
    strategy_code,
    top_n,
    is_default
)
VALUES
    (
        'B1 - Nearest feasible',
        1,
        'nearest_feasible',
        5,
        FALSE
    ),
    (
        'B2 - Pick frequency',
        1,
        'pick_frequency',
        5,
        FALSE
    );
