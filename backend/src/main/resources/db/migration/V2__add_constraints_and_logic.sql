SET ROLE warehouse_owner;

CREATE UNIQUE INDEX uq_zones_one_per_special_type
    ON zones (warehouse_id, zone_type)
    WHERE zone_type IN ('receiving', 'picking', 'shipping');

CREATE UNIQUE INDEX uq_strategy_configs_single_default
    ON strategy_configs (is_default)
    WHERE is_default;

CREATE UNIQUE INDEX uq_batches_arrival_seq
    ON batches (warehouse_id, arrival_seq)
    WHERE arrival_seq IS NOT NULL;

CREATE UNIQUE INDEX uq_sim_ops_one_placement_per_batch
    ON sim_operations (run_id, strategy_config_id, batch_id)
    WHERE operation_type IN ('putaway', 'rejected');

CREATE UNIQUE INDEX uq_single_live_warehouse
    ON warehouses (kind)
    WHERE kind = 'live';

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

ALTER TABLE zones
    ADD CONSTRAINT chk_zones_tiers CHECK (
        (zone_type = 'storage' AND tier_to_picking IS NOT NULL AND tier_to_shipping IS NOT NULL)
            OR
        (zone_type <> 'storage' AND tier_to_picking IS NULL AND tier_to_shipping IS NULL)
        );

ALTER TABLE locations
    ADD CONSTRAINT chk_locations_distances CHECK (
        distance_to_receiving_m >= 0 AND distance_to_picking_m >= 0 AND distance_to_shipping_m >= 0
        );

ALTER TABLE compat_conflicts
    ADD CONSTRAINT chk_conflicts_canonical CHECK (group_a_id < group_b_id);

ALTER TABLE products
    ADD CONSTRAINT chk_products_positive CHECK (
        length_mm > 0 AND width_mm > 0 AND height_mm > 0
            AND unit_volume_dm3 > 0 AND unit_weight_kg > 0
            AND (shelf_life_days IS NULL OR shelf_life_days > 0)
        ),

  ADD CONSTRAINT chk_products_unit_volume CHECK (
    unit_volume_dm3 * 1000000 <= length_mm::numeric * width_mm * height_mm
  );

ALTER TABLE batches
    ADD CONSTRAINT chk_batches_positive CHECK (quantity > 0 AND volume_dm3 > 0 AND weight_kg > 0),
  ADD CONSTRAINT chk_batches_expiry CHECK (expiry_date IS NULL OR expiry_date > receiving_date),
  ADD CONSTRAINT chk_batches_status_location CHECK (
    (status = 'placed' AND current_location_id IS NOT NULL AND placed_at IS NOT NULL)
    OR (status = 'awaiting_putaway' AND current_location_id IS NULL AND placed_at IS NULL)
  );

ALTER TABLE outbound_history
    ADD CONSTRAINT chk_outbound_nonneg CHECK (quantity > 0 AND picks_count > 0);

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

  ADD CONSTRAINT chk_items_explanation CHECK (length(btrim(explanation_text)) > 0);

ALTER TABLE experiments
    ADD CONSTRAINT chk_experiments_threshold CHECK (low_shelf_life_days IS NULL OR low_shelf_life_days > 0),
  ADD CONSTRAINT chk_experiments_outcome_after_lock CHECK (outcome_level IS NULL OR locked_at IS NOT NULL);

ALTER TABLE experiments
    ADD CONSTRAINT chk_experiments_pinned_codes CHECK (
        b1_code = 'nearest_feasible' AND b2_code = 'pick_frequency' AND optimized_code = 'weighted_score'
        );

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

CREATE VIEW v_product_destination_zone AS
SELECT p.id AS product_id, z.id AS destination_zone_id
FROM products p
         JOIN zones z ON z.warehouse_id = p.warehouse_id AND z.zone_type::text = p.outbound_flow::text;

CREATE VIEW v_warehouses_missing_special_zones AS
SELECT w.id AS warehouse_id, t.zone_type
FROM warehouses w
         CROSS JOIN (VALUES ('receiving'::zone_type), ('picking'::zone_type), ('shipping'::zone_type)) AS t(zone_type)
WHERE NOT EXISTS (SELECT 1 FROM zones z WHERE z.warehouse_id = w.id AND z.zone_type = t.zone_type);

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
