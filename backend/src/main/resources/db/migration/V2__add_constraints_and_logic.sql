-- Flyway V2: add constraints and database-side logic.
-- Source: warehouse_slotting_constraints_v3_2_1.sql
-- Objects: CHECK constraints, partial unique indexes, functions, triggers and views.
-- Application-level rules documented in the source remain implemented in Java services.
-- Objects created by this migration are owned by warehouse_owner.

SET ROLE warehouse_owner;

-- Constraints, triggers, views and helper functions that the DBML diagram cannot express (v3.2, lean).
-- Run after the tables generated from warehouse_slotting_schema_v3_2.dbml exist.
-- Tested against a real PostgreSQL instance (structure, constraints, triggers, helper functions).
--
-- v3.2 differences from v3.1.1:
--   * location_distances -> three columns of locations; zone_access_tiers -> two columns of zones.
--     Their CHECKs and the two completeness views are replaced by chk_zones_tiers, chk_locations_distances
--     and v_warehouses_missing_special_zones.
--   * protect_final_validation_run() fixed: it evaluated NEW.status for sim_operations / sim_run_metrics rows
--     (no such field), so every insert into a running validation run failed.
--   * new trigger on scenarios: a scenario with a final validation run cannot be deleted (a cascade from the
--     scenario, the experiment or the warehouse used to bypass trigger 3.2).
--
-- v3.1 differences from v3:
--   * 2 trigger functions (4 CREATE TRIGGER statements) instead of 9 triggers (section 3); the other rules
--     are enforced by the service layer (section 7).
--   * experiment_strategies / sim_run_strategies are gone; experiments pins its three configurations.
--   * new partial indexes (arrival order, one placement per batch, single live warehouse).
--   * new CHECKs: non-empty explanation, unit volume vs dimensions, pinned strategy codes.

-- =====================================================================
-- 1. Partial unique indexes
-- =====================================================================

-- AC4: one receiving, one picking and one shipping zone per warehouse. This is what makes
-- "destination zone = the zone whose type equals products.outbound_flow" unambiguous.
CREATE UNIQUE INDEX uq_zones_one_per_special_type
  ON zones (warehouse_id, zone_type)
  WHERE zone_type IN ('receiving', 'picking', 'shipping');

-- At most one default strategy configuration for the live system.
CREATE UNIQUE INDEX uq_strategy_configs_single_default
  ON strategy_configs (is_default)
  WHERE is_default;

-- v3.1: arrival order is unique inside a warehouse, so all strategies see the same sequence (NFR8, US6.1).
CREATE UNIQUE INDEX uq_batches_arrival_seq
  ON batches (warehouse_id, arrival_seq)
  WHERE arrival_seq IS NOT NULL;

-- v3.1: a batch is placed or rejected at most once per strategy in a run (relocations may repeat).
CREATE UNIQUE INDEX uq_sim_ops_one_placement_per_batch
  ON sim_operations (run_id, strategy_config_id, batch_id)
  WHERE operation_type IN ('putaway', 'rejected');

-- v3.1: exactly one live warehouse (scope: one warehouse is modeled; simulation warehouses are technical).
CREATE UNIQUE INDEX uq_single_live_warehouse
  ON warehouses (kind)
  WHERE kind = 'live';

-- =====================================================================
-- 2. CHECK constraints
-- =====================================================================

-- ---- locations (US1.2, US1.5, NFR7) ----
ALTER TABLE locations
  ADD CONSTRAINT chk_locations_occupancy CHECK (
    occupied_volume_dm3 >= 0 AND occupied_volume_dm3 <= volume_capacity_dm3
    AND occupied_weight_kg >= 0 AND occupied_weight_kg <= max_weight_kg
  ),
  ADD CONSTRAINT chk_locations_positive CHECK (
    volume_capacity_dm3 > 0 AND max_weight_kg > 0
    AND max_length_mm > 0 AND max_width_mm > 0 AND max_height_mm > 0
  ),
  ADD CONSTRAINT chk_locations_in_storage_zone CHECK (zone_type = 'storage');

-- ---- zones: access tiers exist for storage zones only (v3.2) ----
ALTER TABLE zones
  ADD CONSTRAINT chk_zones_tiers CHECK (
    (zone_type = 'storage' AND tier_to_picking IS NOT NULL AND tier_to_shipping IS NOT NULL)
    OR
    (zone_type <> 'storage' AND tier_to_picking IS NULL AND tier_to_shipping IS NULL)
  );

-- ---- locations: distances to the three special zones (v3.2, US1.1) ----
ALTER TABLE locations
  ADD CONSTRAINT chk_locations_distances CHECK (
    distance_to_receiving_m >= 0 AND distance_to_picking_m >= 0 AND distance_to_shipping_m >= 0
  );

-- ---- compatibility ----
ALTER TABLE compat_conflicts
  ADD CONSTRAINT chk_conflicts_canonical CHECK (group_a_id < group_b_id);

-- ---- products (US1.3) ----
ALTER TABLE products
  ADD CONSTRAINT chk_products_positive CHECK (
    length_mm > 0 AND width_mm > 0 AND height_mm > 0
    AND unit_volume_dm3 > 0 AND unit_weight_kg > 0
    AND (shelf_life_days IS NULL OR shelf_life_days > 0)
  ),
  -- v3.1: a unit cannot be larger than its bounding box (1 dm3 = 1 000 000 mm3).
  ADD CONSTRAINT chk_products_unit_volume CHECK (
    unit_volume_dm3 * 1000000 <= length_mm::numeric * width_mm * height_mm
  );

-- ---- batches (US1.4) ----
ALTER TABLE batches
  ADD CONSTRAINT chk_batches_positive CHECK (quantity > 0 AND volume_dm3 > 0 AND weight_kg > 0),
  ADD CONSTRAINT chk_batches_expiry CHECK (expiry_date IS NULL OR expiry_date > receiving_date),
  ADD CONSTRAINT chk_batches_status_location CHECK (
    (status = 'placed' AND current_location_id IS NOT NULL AND placed_at IS NOT NULL)
    OR (status = 'awaiting_putaway' AND current_location_id IS NULL AND placed_at IS NULL)
  );

ALTER TABLE outbound_history
  ADD CONSTRAINT chk_outbound_nonneg CHECK (quantity > 0 AND picks_count > 0);

-- ---- movements (US1.5, US5.4) ----
ALTER TABLE stock_movements
  ADD CONSTRAINT chk_movements_shape CHECK (
    (movement_type = 'relocation' AND from_location_id IS NOT NULL AND from_location_id <> to_location_id)
    OR (movement_type IN ('putaway', 'import') AND from_location_id IS NULL)
  ),
  ADD CONSTRAINT chk_movements_override_reason CHECK (
    NOT is_override OR (override_reason IS NOT NULL AND length(btrim(override_reason)) > 0)
  );

ALTER TABLE import_jobs
  ADD CONSTRAINT chk_import_counts CHECK (
    total_rows >= 0 AND imported_rows >= 0 AND error_rows >= 0
    AND imported_rows + error_rows <= total_rows
  );

-- ---- classification (US2.1, US2.2) ----
ALTER TABLE classification_runs
  ADD CONSTRAINT chk_class_runs_shares CHECK (
    abc_a_share > 0 AND abc_b_share > 0 AND abc_a_share + abc_b_share < 1
  ),
  ADD CONSTRAINT chk_class_runs_cv CHECK (cv_x_max > 0 AND cv_x_max < cv_y_max),
  ADD CONSTRAINT chk_class_runs_window CHECK (
    window_start IS NULL OR window_end IS NULL OR window_start <= window_end
  );

ALTER TABLE product_classes
  ADD CONSTRAINT chk_product_classes_counts CHECK (
    movement_count >= 0 AND (cumulative_share IS NULL OR (cumulative_share >= 0 AND cumulative_share <= 1))
  );

-- ---- strategy configurations ----
-- Weights (and the ranking threshold) belong only to the weighted strategy; B1 and B2 have none.
ALTER TABLE strategy_configs
  ADD CONSTRAINT chk_strategy_top_n CHECK (top_n > 0),
  ADD CONSTRAINT chk_strategy_weights CHECK (
    (
      strategy_code = 'weighted_score'
      AND w_class IS NOT NULL AND w_distance IS NOT NULL
      AND w_shelf_life IS NOT NULL AND w_density IS NOT NULL
      AND low_shelf_life_days IS NOT NULL AND low_shelf_life_days > 0
      AND w_class >= 0 AND w_distance >= 0 AND w_shelf_life >= 0 AND w_density >= 0
      AND abs(w_class + w_distance + w_shelf_life + w_density - 1) < 0.001
    )
    OR
    (
      strategy_code <> 'weighted_score'
      AND w_class IS NULL AND w_distance IS NULL
      AND w_shelf_life IS NULL AND w_density IS NULL
      AND low_shelf_life_days IS NULL
    )
  );

ALTER TABLE recommendations
  ADD CONSTRAINT chk_recommendations_count CHECK (feasible_count >= 0);
ALTER TABLE recommendation_items
  ADD CONSTRAINT chk_items_rank CHECK (rank > 0),
  -- v3.1: SI2 counts only recommendations with a real explanation.
  ADD CONSTRAINT chk_items_explanation CHECK (length(btrim(explanation_text)) > 0);

-- ---- experiments ----
ALTER TABLE experiments
  ADD CONSTRAINT chk_experiments_threshold CHECK (low_shelf_life_days IS NULL OR low_shelf_life_days > 0),
  ADD CONSTRAINT chk_experiments_outcome_after_lock CHECK (outcome_level IS NULL OR locked_at IS NOT NULL);

-- v3.1: each configuration field of an experiment can only hold its own strategy kind
-- (together with the composite FK to strategy_configs(id, strategy_code)).
ALTER TABLE experiments
  ADD CONSTRAINT chk_experiments_pinned_codes CHECK (
    b1_code = 'nearest_feasible' AND b2_code = 'pick_frequency' AND optimized_code = 'weighted_score'
  );

-- ---- runs and simulated operations ----
ALTER TABLE sim_runs
  ADD CONSTRAINT chk_sim_runs_completed CHECK (
    status <> 'completed' OR (finished_at IS NOT NULL AND is_valid IS NOT NULL)
  );

ALTER TABLE sim_operations
  ADD CONSTRAINT chk_sim_ops_shape CHECK (
    (operation_type = 'putaway'
       AND from_location_id IS NULL AND to_location_id IS NOT NULL
       AND distance_m IS NOT NULL AND distance_m >= 0)
    OR (operation_type = 'relocation'
       AND from_location_id IS NOT NULL AND to_location_id IS NOT NULL
       AND from_location_id <> to_location_id
       AND checkpoint_no IS NOT NULL AND distance_m IS NULL)
    OR (operation_type = 'rejected'
       AND from_location_id IS NULL AND to_location_id IS NULL AND distance_m IS NULL)
  ),
  ADD CONSTRAINT chk_sim_ops_seq CHECK (seq_no >= 0);

ALTER TABLE sim_run_metrics
  ADD CONSTRAINT chk_sim_metrics_checkpoint CHECK (checkpoint_no >= -1);

-- =====================================================================
-- 3. Triggers (three functions, five CREATE TRIGGER statements; they protect experiment results)
-- =====================================================================

-- ---- 3.1 Strategy configurations: frozen or already used weights are immutable ----
-- Frozen: weights/thresholds cannot change, the row cannot be deleted, and it cannot be unfrozen.
-- Used (a simulated operation, metric or recommendation refers to it): weights/thresholds cannot change
-- either; tuning a new weight set means inserting a NEW version (same name, version + 1).
-- is_default and top_n stay editable, so the tuned configuration can become the default after freezing.
CREATE OR REPLACE FUNCTION protect_strategy_config() RETURNS trigger AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF OLD.frozen_at IS NOT NULL THEN
      RAISE EXCEPTION 'strategy_configs row % is frozen and cannot be deleted', OLD.id;
    END IF;
    RETURN OLD;
  END IF;

  IF OLD.frozen_at IS NOT NULL AND NEW.frozen_at IS DISTINCT FROM OLD.frozen_at THEN
    RAISE EXCEPTION 'strategy_configs row % is frozen and cannot be unfrozen', OLD.id;
  END IF;

  IF (NEW.name, NEW.version, NEW.strategy_code, NEW.w_class, NEW.w_distance,
      NEW.w_shelf_life, NEW.w_density, NEW.low_shelf_life_days)
     IS DISTINCT FROM
     (OLD.name, OLD.version, OLD.strategy_code, OLD.w_class, OLD.w_distance,
      OLD.w_shelf_life, OLD.w_density, OLD.low_shelf_life_days) THEN
    IF OLD.frozen_at IS NOT NULL THEN
      RAISE EXCEPTION 'strategy_configs row % is frozen; create a new version instead', OLD.id;
    END IF;
    IF EXISTS (SELECT 1 FROM sim_operations   WHERE strategy_config_id = OLD.id)
       OR EXISTS (SELECT 1 FROM sim_run_metrics WHERE strategy_config_id = OLD.id)
       OR EXISTS (SELECT 1 FROM recommendations WHERE strategy_config_id = OLD.id) THEN
      RAISE EXCEPTION 'strategy_configs row % is already used by runs or recommendations; create a new version instead', OLD.id;
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_protect_strategy_config
  BEFORE UPDATE OR DELETE ON strategy_configs
  FOR EACH ROW EXECUTE FUNCTION protect_strategy_config();

-- ---- 3.2 A finished validation run is final ----
-- Once a validation run is completed or failed, neither the run nor its operations and metrics can be
-- changed, added or deleted, so validation results cannot be re-run until they look better. A failed or
-- invalid run stays in the history; a corrected re-run is a NEW sim_runs row. A scenario may have only
-- one completed run with is_valid = true. Tuning runs are not restricted.
-- Reproducibility (NFR8) is therefore tested on a tuning scenario.
-- During a cascade delete the parent run row is already gone, so the lookup finds nothing and the
-- cascade of a non-final run passes; deleting a scenario with a final validation run is blocked by the
-- trigger on sim_runs.
CREATE OR REPLACE FUNCTION protect_final_validation_run() RETURNS trigger AS $$
DECLARE
  v_row      jsonb;
  v_run      bigint;
  v_status   run_status;
  v_scenario bigint;
  v_purpose  scenario_purpose;
BEGIN
  IF TG_OP = 'INSERT' THEN v_row := to_jsonb(NEW); ELSE v_row := to_jsonb(OLD); END IF;

  IF TG_TABLE_NAME = 'sim_runs' THEN
    v_run := (v_row ->> 'id')::bigint;
  ELSE
    v_run := (v_row ->> 'run_id')::bigint;
  END IF;

  SELECT r.status, r.scenario_id, s.purpose
    INTO v_status, v_scenario, v_purpose
    FROM sim_runs r
    JOIN scenarios s ON s.id = r.scenario_id
   WHERE r.id = v_run;

  IF FOUND AND v_purpose = 'validation' THEN
    IF v_status IN ('completed', 'failed') THEN
      RAISE EXCEPTION 'validation run % is final (%): % on % is not allowed',
        v_run, v_status, TG_OP, TG_TABLE_NAME;
    END IF;
    IF TG_TABLE_NAME = 'sim_runs' AND TG_OP = 'UPDATE' THEN
      IF NEW.status = 'completed' AND NEW.is_valid IS TRUE
         AND EXISTS (SELECT 1 FROM sim_runs
                      WHERE scenario_id = v_scenario AND id <> v_run
                        AND status = 'completed' AND is_valid) THEN
        RAISE EXCEPTION 'scenario % already has a completed valid validation run', v_scenario;
      END IF;
    END IF;
  END IF;

  IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- A scenario with a final validation run cannot be deleted. The sim_runs trigger alone is not enough: during a
-- cascade from the scenario, the experiment or the warehouse the scenario row is already gone when it looks it up.
CREATE OR REPLACE FUNCTION protect_scenario_with_final_run() RETURNS trigger AS $$
BEGIN
  IF OLD.purpose = 'validation'
     AND EXISTS (SELECT 1 FROM sim_runs
                  WHERE scenario_id = OLD.id AND status IN ('completed', 'failed')) THEN
    RAISE EXCEPTION 'scenario % has a final validation run and cannot be deleted', OLD.id;
  END IF;
  RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_final_run_sim_runs
  BEFORE UPDATE OR DELETE ON sim_runs
  FOR EACH ROW EXECUTE FUNCTION protect_final_validation_run();

CREATE TRIGGER trg_final_run_sim_operations
  BEFORE INSERT OR UPDATE OR DELETE ON sim_operations
  FOR EACH ROW EXECUTE FUNCTION protect_final_validation_run();

CREATE TRIGGER trg_final_run_sim_run_metrics
  BEFORE INSERT OR UPDATE OR DELETE ON sim_run_metrics
  FOR EACH ROW EXECUTE FUNCTION protect_final_validation_run();

CREATE TRIGGER trg_protect_scenario_with_final_run
  BEFORE DELETE ON scenarios
  FOR EACH ROW EXECUTE FUNCTION protect_scenario_with_final_run();

-- =====================================================================
-- 4. Views for data validation
-- =====================================================================

-- Destination zone of each product (KPI1): the warehouse zone whose type equals the outbound flow.
CREATE VIEW v_product_destination_zone AS
SELECT p.id AS product_id, z.id AS destination_zone_id
FROM products p
JOIN zones z ON z.warehouse_id = p.warehouse_id AND z.zone_type::text = p.outbound_flow::text;

-- Warehouses lacking a receiving, picking or shipping zone (the three distances of every location refer to them).
CREATE VIEW v_warehouses_missing_special_zones AS
SELECT w.id AS warehouse_id, t.zone_type
FROM warehouses w
CROSS JOIN (VALUES ('receiving'::zone_type), ('picking'::zone_type), ('shipping'::zone_type)) AS t(zone_type)
WHERE NOT EXISTS (SELECT 1 FROM zones z WHERE z.warehouse_id = w.id AND z.zone_type = t.zone_type);

-- Drift between the denormalized occupancy of live locations and the sum of their placed batches.
-- Must be empty after every test (NFR7).
CREATE VIEW v_location_occupancy_drift AS
SELECT l.id AS location_id,
       l.occupied_volume_dm3, COALESCE(SUM(b.volume_dm3), 0) AS batches_volume_dm3,
       l.occupied_weight_kg,  COALESCE(SUM(b.weight_kg), 0)  AS batches_weight_kg
FROM locations l
JOIN warehouses w ON w.id = l.warehouse_id AND w.kind = 'live'
LEFT JOIN batches b ON b.current_location_id = l.id AND b.status = 'placed'
GROUP BY l.id
HAVING l.occupied_volume_dm3 <> COALESCE(SUM(b.volume_dm3), 0)
    OR l.occupied_weight_kg  <> COALESCE(SUM(b.weight_kg), 0);

-- =====================================================================
-- 5. Helper functions
-- =====================================================================

-- GDPR erasure on request: keep the row (so the audit trail keeps a pseudonymous actor), scrub the person.
CREATE OR REPLACE FUNCTION anonymize_user(p_user_id bigint) RETURNS void AS $$
BEGIN
  UPDATE users
  SET login = 'anon-' || id,
      full_name = 'anonymized',
      password_hash = '!',
      is_active = false,
      anonymized_at = now()
  WHERE id = p_user_id;
END;
$$ LANGUAGE plpgsql;

-- Removes a simulation warehouse with everything in it. The scenario goes first (it cascades to
-- runs, strategies, operations and metrics); a locked experiment blocks the purge by design.
CREATE OR REPLACE FUNCTION purge_simulation_warehouse(p_warehouse_id bigint) RETURNS void AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM warehouses WHERE id = p_warehouse_id AND kind = 'simulation') THEN
    RAISE EXCEPTION 'warehouse % is not a simulation warehouse', p_warehouse_id;
  END IF;
  DELETE FROM scenarios WHERE warehouse_id = p_warehouse_id;
  DELETE FROM import_jobs WHERE warehouse_id = p_warehouse_id;
  DELETE FROM batches WHERE warehouse_id = p_warehouse_id;
  DELETE FROM outbound_history WHERE product_id IN (SELECT id FROM products WHERE warehouse_id = p_warehouse_id);
  DELETE FROM locations WHERE warehouse_id = p_warehouse_id;
  DELETE FROM products WHERE warehouse_id = p_warehouse_id;
  DELETE FROM compat_conflicts WHERE warehouse_id = p_warehouse_id;
  DELETE FROM compat_groups WHERE warehouse_id = p_warehouse_id;
  DELETE FROM zones WHERE warehouse_id = p_warehouse_id;
  DELETE FROM warehouses WHERE id = p_warehouse_id;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- 6. Putaway pattern (documentation for the Spring Boot implementation, NFR7 / R6)
-- =====================================================================
-- Capacity: chk_locations_occupancy is the last line of defence, so concurrent putaways can never overrun.
-- Compatibility: two concurrent putaways of conflicting groups into one location do not break capacity,
-- so the CHECK cannot catch them. Serialize them on the location row inside the confirmation transaction:
--
--   UPDATE locations
--      SET occupied_volume_dm3 = occupied_volume_dm3 + :volume,
--          occupied_weight_kg  = occupied_weight_kg  + :weight,
--          version             = version + 1
--    WHERE id = :location_id AND version = :version_seen_by_the_recommendation;
--   -- 0 rows updated: the location changed since the list was built -> reject, refresh the list (UC2 A2).
--
-- Then, in the same transaction, update the batch, insert the stock_movements row, and re-check
-- compatibility against the batches currently placed in the location.

-- =====================================================================
-- 7. Rules enforced by the service layer (ExperimentService), each covered by a unit test
-- =====================================================================
-- v3 enforced these by triggers; v3.1 moves them to the application to keep the database simple.
--
--  R-S1  Simulation warehouses are stateless: batches stay 'awaiting_putaway' without a location,
--        locations keep occupied_volume_dm3 = occupied_weight_kg = 0.
--  R-S2  A scenario must use a warehouse of kind 'simulation'.
--  R-S3  Locking an experiment (irreversible) requires: low_shelf_life_days set; b1/b2/optimized
--        configurations all set and all frozen (frozen_at IS NOT NULL); validation scenarios of all three
--        kinds (ordinary, high_utilization, short_shelf_life).
--  R-S4  After the lock: protocol, low_shelf_life_days, the three configuration fields and the set of
--        scenarios do not change; the experiment is not deleted. (A scenario with a final validation run
--        cannot be deleted anyway: trigger on scenarios, section 3.2.)
--  R-S5  Run gate: a tuning run is created only before the lock, a validation run only after it.
--  R-S6  sim_operations / sim_run_metrics refer only to configurations of the run's experiment.
--  R-S7  Constraints US1.4 etc. that depend on several rows (compatibility re-check, batch volume and
--        weight calculation, archived products not offered) stay in the putaway service, see section 6.
