# Data Dictionary: Intelligent Warehouse Slotting Optimization System

PostgreSQL schema v3.2.1. Tables, enums, indexes and foreign keys come from the DBML; CHECK rules, partial unique indexes, triggers, functions and views from the constraints SQL.

## Tables

| # | Table | Group | Purpose | BA reference |
|---|---|---|---|---|
| 1 | `warehouses` | E1 Warehouse data | Warehouses of the system: the single live (demo) warehouse and one simulation warehouse owned by each experiment scenario. | AC4; E1; US1.1 |
| 2 | `zones` | E1 Warehouse data | Zones of a warehouse: exactly one receiving, one picking and one shipping zone, and any number of storage zones with access tiers. | AC4; E1; US1.1 |
| 3 | `locations` | E1 Warehouse data | Storage locations (bins) in storage zones: capacity limits, distances to the receiving, picking and shipping zones, and current occupancy. | E1, E3; US1.1, US1.2, US3.1 |
| 4 | `compat_groups` | E1 Warehouse data | Product compatibility groups (for example food, chemicals). | E3; US3.2 |
| 5 | `compat_conflicts` | E1 Warehouse data | Pairs of incompatible groups that must not share a location. A conflict is symmetric and stored once (group_a_id < group_b_id). | E3; US3.2 |
| 6 | `products` | E1 Warehouse data | Product catalog: dimensions, weight, compatibility group, shelf life and outbound flow. | E1; US1.3 |
| 7 | `batches` | E1 Warehouse data | Incoming batches of products: quantity, volume, weight, dates, status and current location. | E1, E5; US1.4, US5.1 |
| 8 | `outbound_history` | E1 Warehouse data | Daily pick history per product. Source of the ABC/XYZ classes and of the B2 pick frequency. | E1, E2; US1.7, US2.1, US2.2 |
| 9 | `stock_movements` | E1 Warehouse data | Audit of live putaways and relocations, including operator overrides. Simulated operations are stored in sim_operations. | E1, E5; US1.5, US5.3, US5.4 |
| 10 | `import_jobs` | E1 Warehouse data | CSV import jobs with progress counters and row-level errors. | E1; US1.7 |
| 11 | `users` | E8 Access | System users with roles. Erasure on request is done by anonymization (anonymize_user), so the audit trail keeps its integrity. | E8; US8.1-US8.3; GDPR (Table 2.13) |
| 12 | `classification_runs` | E2 Classification | Runs of the ABC/XYZ classification for the live warehouse, with the pick-history window and thresholds used. | E2; US2.1, US2.2 |
| 13 | `product_classes` | E2 Classification | ABC and XYZ class of each product in a classification run. | E2; US2.1, US2.2 |
| 14 | `strategy_configs` | E4 Recommendation | Placement strategy configurations: B1, B2 and the weighted optimized strategy with weights and thresholds. A configuration is frozen after tuning. | E4, E6; US4.5, US4.6, US6.1 |
| 15 | `recommendations` | E4 Recommendation | A recommendation request for a batch: the strategy used, the number of feasible locations and the reasons for exclusion. | E4; US3.1, US4.1 |
| 16 | `recommendation_items` | E4 Recommendation | Ranked locations of a recommendation with the score and the explanation shown to the operator. | E4, E5; US4.1, US4.4, US5.2 |
| 17 | `experiments` | E6 Experiments | Experiments comparing B1, B2 and the optimized strategy: protocol, evaluation threshold, lock time and outcome level. | E6; US6.1, US6.3; Table 2.15 |
| 18 | `scenarios` | E6 Experiments | Simulation scenarios of an experiment: kind, purpose (tuning or validation), seed and generator parameters. Each scenario owns one simulation warehouse. | E6; US6.1; NFR8 |
| 19 | `sim_runs` | E6 Experiments | Runs of a scenario: status, validity and the simulator version. | E6; US6.1; NFR8 |
| 20 | `sim_operations` | E6 Experiments | Raw simulated operations (putaway, relocation, rejection) of each strategy in a run. The data behind KPI1 and KPI2. | E6; US6.5 |
| 21 | `sim_run_metrics` | E6 Experiments | Metric values per run, strategy and checkpoint: KPI1-KPI4, SI1, SI2, rejections and run time. | E6; US6.2, US6.3 |

## Columns

### `warehouses`

Warehouses of the system: the single live (demo) warehouse and one simulation warehouse owned by each experiment scenario.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Surrogate primary key (identity). |
| `name` | varchar(128) | no |  |  | Display name of the warehouse. |
| `kind` | warehouse_kind | no | live |  | live = the demo warehouse (exactly one in the database); simulation = a warehouse owned by one scenario, stateless. Values: live, simulation. |
| `created_at` | timestamptz | no | now() |  | Creation timestamp. |

### `zones`

Zones of a warehouse: exactly one receiving, one picking and one shipping zone, and any number of storage zones with access tiers.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Surrogate primary key (identity). |
| `warehouse_id` | bigint | no |  | FK -> warehouses.id | Owning warehouse. |
| `code` | varchar(32) | no |  |  | Short code, unique within the warehouse. |
| `name` | varchar(128) | no |  |  | Display name. |
| `zone_type` | zone_type | no |  |  | receiving, picking and shipping (one each per warehouse) or storage (any number). Values: receiving, picking, shipping, storage. |
| `tier_to_picking` | access_tier | yes |  |  | Access tier of a storage zone relative to the picking zone: fast (fast-access zone), middle or remaining. NULL for non-storage zones. Values: fast, middle, remaining. |
| `tier_to_shipping` | access_tier | yes |  |  | The same, relative to the shipping zone. NULL for non-storage zones. Values: fast, middle, remaining. |

### `locations`

Storage locations (bins) in storage zones: capacity limits, distances to the receiving, picking and shipping zones, and current occupancy.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Surrogate primary key (identity). |
| `warehouse_id` | bigint | no |  | FK -> warehouses.id; FK -> zones.(id, warehouse_id, zone_type) [composite] | Owning warehouse. |
| `zone_id` | bigint | no |  | FK -> zones.(id, warehouse_id, zone_type) [composite] | Storage zone that contains the location. |
| `zone_type` | zone_type | no | storage | FK -> zones.(id, warehouse_id, zone_type) [composite] | Always storage. Together with the composite foreign key it prevents locations in receiving, picking or shipping zones. Values: receiving, picking, shipping, storage. |
| `code` | varchar(32) | no |  |  | Location code, unique within the warehouse. |
| `volume_capacity_dm3` | numeric(12,3) | no |  |  | Usable volume capacity, dm3. |
| `max_weight_kg` | numeric(12,3) | no |  |  | Maximum load, kg. |
| `max_length_mm` | integer | no |  |  | Maximum item length, mm. |
| `max_width_mm` | integer | no |  |  | Maximum item width, mm. |
| `max_height_mm` | integer | no |  |  | Maximum item height, mm. |
| `distance_to_receiving_m` | numeric(10,2) | no |  |  | Distance from the receiving zone, m (first leg of KPI1). |
| `distance_to_picking_m` | numeric(10,2) | no |  |  | Distance to the picking zone, m (second leg of KPI1 for picking products). |
| `distance_to_shipping_m` | numeric(10,2) | no |  |  | Distance to the shipping zone, m (second leg of KPI1 for shipping products). |
| `is_active` | boolean | no | true |  | Inactive locations are never recommended (US1.1). |
| `occupied_volume_dm3` | numeric(12,3) | no | 0 |  | Occupied volume, dm3. Denormalized, live warehouse only, never above the capacity. |
| `occupied_weight_kg` | numeric(12,3) | no | 0 |  | Occupied weight, kg. Denormalized, live warehouse only, never above the maximum load. |
| `version` | bigint | no | 0 |  | Optimistic-lock counter, bumped on every putaway so that concurrent putaways cannot overfill a location (NFR7). |

### `compat_groups`

Product compatibility groups (for example food, chemicals).

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Surrogate primary key (identity). |
| `warehouse_id` | bigint | no |  | FK -> warehouses.id | Warehouse the group belongs to (a generator can vary compatibility per scenario). |
| `code` | varchar(32) | no |  |  | Group code, unique within the warehouse. |
| `name` | varchar(128) | no |  |  | Display name. |

### `compat_conflicts`

Pairs of incompatible groups that must not share a location. A conflict is symmetric and stored once (group_a_id < group_b_id).

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `warehouse_id` | bigint | no |  | FK -> compat_groups.(id, warehouse_id) [composite]; FK -> compat_groups.(id, warehouse_id) [composite] | Owning warehouse. |
| `group_a_id` | bigint | no |  | PK; FK -> compat_groups.(id, warehouse_id) [composite] | First group of the conflicting pair (the smaller id). |
| `group_b_id` | bigint | no |  | PK; FK -> compat_groups.(id, warehouse_id) [composite] | Second group of the pair (the larger id). |

### `products`

Product catalog: dimensions, weight, compatibility group, shelf life and outbound flow.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Surrogate primary key (identity). |
| `warehouse_id` | bigint | no |  | FK -> warehouses.id; FK -> compat_groups.(id, warehouse_id) [composite] | Owning warehouse. |
| `sku` | varchar(64) | no |  |  | Stock-keeping unit, unique within the warehouse (US1.3). |
| `name` | varchar(255) | no |  |  | Product name. |
| `length_mm` | integer | no |  |  | Unit length, mm. |
| `width_mm` | integer | no |  |  | Unit width, mm. |
| `height_mm` | integer | no |  |  | Unit height, mm. |
| `unit_volume_dm3` | numeric(14,6) | no |  |  | Volume of one unit, dm3. Not larger than the bounding box. |
| `unit_weight_kg` | numeric(10,3) | no |  |  | Weight of one unit, kg. |
| `compat_group_id` | bigint | yes |  | FK -> compat_groups.(id, warehouse_id) [composite] | Compatibility group; NULL = compatible with everything. |
| `shelf_life_days` | integer | yes |  |  | Shelf life in days; NULL = no expiry date. |
| `outbound_flow` | outbound_flow | no |  |  | Destination zone type for KPI1: picking or shipping. Values: picking, shipping. |
| `is_archived` | boolean | no | false |  | Archived products are not offered when registering a batch (US1.3). |
| `created_at` | timestamptz | no | now() |  | Creation timestamp. |

### `batches`

Incoming batches of products: quantity, volume, weight, dates, status and current location.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Surrogate primary key (identity). |
| `warehouse_id` | bigint | no |  | FK -> warehouses.id; FK -> products.(id, warehouse_id) [composite]; FK -> locations.(id, warehouse_id) [composite] | Owning warehouse. |
| `product_id` | bigint | no |  | FK -> products.(id, warehouse_id) [composite] | Product of the batch. |
| `batch_code` | varchar(64) | no |  |  | Batch code, unique within the warehouse; makes CSV import idempotent (US1.7). |
| `quantity` | integer | no |  |  | Number of units, greater than 0. |
| `volume_dm3` | numeric(12,3) | no |  |  | Total volume, dm3, calculated at registration (US1.4). |
| `weight_kg` | numeric(12,3) | no |  |  | Total weight, kg, calculated at registration. |
| `receiving_date` | date | no |  |  | Date the batch was received. |
| `expiry_date` | date | yes |  |  | Expiry date; NULL = no expiry. Must be later than the receiving date. |
| `status` | batch_status | no | awaiting_putaway |  | awaiting_putaway (registered) or placed (putaway confirmed). Values: awaiting_putaway, placed. |
| `current_location_id` | bigint | yes |  | FK -> locations.(id, warehouse_id) [composite] | Location of a placed batch; NULL while awaiting putaway. |
| `placed_at` | timestamptz | yes |  |  | Time of placement; set exactly when the batch is placed. |
| `arrival_seq` | integer | yes |  |  | Arrival order inside the warehouse; the same sequence for all strategies in a simulation (NFR8, US6.1). |
| `created_by` | bigint | yes |  | FK -> users.id | User who registered the batch. |
| `created_at` | timestamptz | no | now() |  | Creation timestamp. |

### `outbound_history`

Daily pick history per product. Source of the ABC/XYZ classes and of the B2 pick frequency.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Surrogate primary key (identity). |
| `product_id` | bigint | no |  | FK -> products.id | Product that was picked. |
| `event_date` | date | no |  |  | Day of the picks; unique per product and day, so re-importing a file creates no duplicates. |
| `quantity` | integer | no |  |  | Units picked that day, greater than 0. |
| `picks_count` | integer | no |  |  | Number of pick lines that day (movement frequency), greater than 0. |

### `stock_movements`

Audit of live putaways and relocations, including operator overrides. Simulated operations are stored in sim_operations.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Surrogate primary key (identity). |
| `warehouse_id` | bigint | no |  | FK -> batches.(id, warehouse_id) [composite]; FK -> locations.(id, warehouse_id) [composite]; FK -> locations.(id, warehouse_id) [composite]; FK -> recommendation_items.(id, warehouse_id) [composite] | Owning warehouse. |
| `batch_id` | bigint | no |  | FK -> batches.(id, warehouse_id) [composite] | Batch that was moved. |
| `from_location_id` | bigint | yes |  | FK -> locations.(id, warehouse_id) [composite] | Source location; NULL for putaway and import. |
| `to_location_id` | bigint | no |  | FK -> locations.(id, warehouse_id) [composite] | Destination location. |
| `movement_type` | movement_type | no |  |  | putaway, relocation or import. Values: putaway, relocation, import. |
| `recommendation_item_id` | bigint | yes |  | FK -> recommendation_items.(id, warehouse_id) [composite] | Recommended item that was confirmed; NULL for overrides and imports. |
| `is_override` | boolean | no | false |  | True when the operator chose a location other than the recommended ones. |
| `override_reason` | text | yes |  |  | Mandatory, non-empty reason when is_override is true (US5.4). |
| `performed_by` | bigint | yes |  | FK -> users.id | User who confirmed the movement. |
| `performed_at` | timestamptz | no | now() |  | Time of the movement. |

### `import_jobs`

CSV import jobs with progress counters and row-level errors.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Surrogate primary key (identity). |
| `warehouse_id` | bigint | no |  | FK -> warehouses.id | Owning warehouse. |
| `entity_type` | import_entity | no |  |  | Kind of imported data. Values: zones, locations, compat_groups, compat_conflicts, products, batches, outbound_history. |
| `file_name` | varchar(255) | no |  |  | Name of the imported CSV file. |
| `status` | job_status | no | pending |  | pending, running, completed or failed. Values: pending, running, completed, failed. |
| `total_rows` | integer | no | 0 |  | Rows in the file. |
| `imported_rows` | integer | no | 0 |  | Rows imported successfully. |
| `error_rows` | integer | no | 0 |  | Rows rejected; imported_rows + error_rows never exceeds total_rows. |
| `errors` | jsonb | yes |  |  | Row-level errors (row number and reason), JSON. |
| `created_by` | bigint | yes |  | FK -> users.id | User who started the import. |
| `started_at` | timestamptz | yes |  |  | Start time. |
| `finished_at` | timestamptz | yes |  |  | Finish time. |

### `users`

System users with roles. Erasure on request is done by anonymization (anonymize_user), so the audit trail keeps its integrity.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Surrogate primary key (identity). |
| `login` | varchar(64) | no |  | UNIQUE (case-insensitive, lower(login)) | Login, unique case-insensitively. |
| `full_name` | varchar(128) | no |  |  | Full name (replaced by "anonymized" after erasure). |
| `password_hash` | varchar(255) | no |  |  | Password hash (sized for Argon2); never a plain password (NFR4). |
| `role` | user_role | no |  |  | operator, manager, planner, business_manager or admin. Values: operator, manager, planner, business_manager, admin. |
| `is_active` | boolean | no | true |  | Inactive users cannot log in. |
| `anonymized_at` | timestamptz | yes |  |  | Set by anonymize_user(); the row stays so the audit trail keeps its integrity. |
| `created_at` | timestamptz | no | now() |  | Creation timestamp. |

### `classification_runs`

Runs of the ABC/XYZ classification for the live warehouse, with the pick-history window and thresholds used.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Surrogate primary key (identity). |
| `warehouse_id` | bigint | no |  | FK -> warehouses.id | Live warehouse (the simulator recomputes classes in memory). |
| `calculated_at` | timestamptz | no | now() |  | Time of the calculation. |
| `window_start` | date | yes |  |  | First day of the pick-history window. |
| `window_end` | date | yes |  |  | Last day of the window (not before window_start). |
| `abc_a_share` | numeric(4,3) | no | 0.8 |  | Cumulative share of picks that forms class A (default 0.80). |
| `abc_b_share` | numeric(4,3) | no | 0.15 |  | Share that forms class B (default 0.15); the rest is class C. |
| `cv_x_max` | numeric(4,2) | no | 0.5 |  | Largest coefficient of variation of class X (default 0.5). |
| `cv_y_max` | numeric(4,2) | no | 1 |  | Largest coefficient of variation of class Y (default 1.0); above it is class Z. |

### `product_classes`

ABC and XYZ class of each product in a classification run.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `classification_run_id` | bigint | no |  | PK; FK -> classification_runs.(id, warehouse_id) [composite] | Classification run. |
| `warehouse_id` | bigint | no |  | FK -> classification_runs.(id, warehouse_id) [composite]; FK -> products.(id, warehouse_id) [composite] | Owning warehouse. |
| `product_id` | bigint | no |  | PK; FK -> products.(id, warehouse_id) [composite] | Classified product. |
| `abc_class` | abc_class | no |  |  | A, B or C by movement frequency; C with the note "no data" when there is no history. Values: A, B, C. |
| `xyz_class` | xyz_class | yes |  |  | X, Y or Z by demand variability; NULL when data are insufficient. Values: X, Y, Z. |
| `movement_count` | integer | no |  |  | Picks in the window. |
| `cumulative_share` | numeric(6,5) | yes |  |  | Cumulative share of picks, 0 to 1. |
| `cv` | numeric(8,4) | yes |  |  | Coefficient of variation of demand. |
| `note` | varchar(64) | yes |  |  | "no data" or "insufficient data". |

### `strategy_configs`

Placement strategy configurations: B1, B2 and the weighted optimized strategy with weights and thresholds. A configuration is frozen after tuning.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Surrogate primary key (identity). |
| `name` | varchar(64) | no |  |  | Configuration name; unique together with version. |
| `version` | integer | no | 1 |  | Version number (a new tuning iteration = a new version). |
| `strategy_code` | strategy_code | no |  |  | nearest_feasible (B1), pick_frequency (B2) or weighted_score (optimized). Values: nearest_feasible, pick_frequency, weighted_score. |
| `w_class` | numeric(5,4) | yes |  |  | Weight of the ABC/XYZ class in the score; NULL for B1 and B2. The four weights sum to 1. |
| `w_distance` | numeric(5,4) | yes |  |  | Weight of the distance to the destination zone. |
| `w_shelf_life` | numeric(5,4) | yes |  |  | Weight of the remaining shelf life. |
| `w_density` | numeric(5,4) | yes |  |  | Weight of the packing density. |
| `low_shelf_life_days` | integer | yes |  |  | Ranking threshold of low remaining shelf life (US4.3); weighted_score only. |
| `top_n` | integer | no | 5 |  | Number of ranked locations returned (US4.1). |
| `is_default` | boolean | no | false |  | Default configuration of the live system; at most one. |
| `frozen_at` | timestamptz | yes |  |  | Set when weights are frozen after tuning. A frozen configuration is immutable except top_n and is_default. |
| `created_at` | timestamptz | no | now() |  | Creation timestamp. |

### `recommendations`

A recommendation request for a batch: the strategy used, the number of feasible locations and the reasons for exclusion.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Surrogate primary key (identity). |
| `warehouse_id` | bigint | no |  | FK -> batches.(id, warehouse_id) [composite] | Owning warehouse. |
| `batch_id` | bigint | no |  | FK -> batches.(id, warehouse_id) [composite] | Batch the recommendation is for. |
| `strategy_config_id` | bigint | no |  | FK -> strategy_configs.id | Strategy configuration that produced it. |
| `requested_by` | bigint | yes |  | FK -> users.id | User who requested it. |
| `requested_at` | timestamptz | no | now() |  | Request time. |
| `feasible_count` | integer | no |  |  | Number of locations that passed the hard constraints (0 or more). |
| `rejection_summary` | jsonb | yes |  |  | JSON: reason codes of excluded locations (US3.1). |

### `recommendation_items`

Ranked locations of a recommendation with the score and the explanation shown to the operator.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Surrogate primary key (identity). |
| `recommendation_id` | bigint | no |  | FK -> recommendations.(id, warehouse_id) [composite] | Parent recommendation. |
| `warehouse_id` | bigint | no |  | FK -> recommendations.(id, warehouse_id) [composite]; FK -> locations.(id, warehouse_id) [composite] | Owning warehouse. |
| `rank` | integer | no |  |  | Position in the list, starting from 1. |
| `location_id` | bigint | no |  | FK -> locations.(id, warehouse_id) [composite] | Recommended location. |
| `total_score` | numeric(8,5) | no |  |  | Weighted score of the location. |
| `explanation_text` | text | no |  |  | Plain-language explanation, never empty (US4.4). |
| `factors` | jsonb | no |  |  | JSON: contribution of each factor; the contributions add up to total_score. |

### `experiments`

Experiments comparing B1, B2 and the optimized strategy: protocol, evaluation threshold, lock time and outcome level.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Surrogate primary key (identity). |
| `name` | varchar(128) | no |  |  | Experiment name. |
| `low_shelf_life_days` | integer | yes |  |  | Evaluation threshold of "low remaining shelf life" for KPI2(b) and SI1, identical for all strategies. |
| `protocol` | jsonb | no |  |  | JSON protocol: checkpoints, relocation rule, B2 zone rules. Fixed at the lock. |
| `created_at` | timestamptz | no | now() |  | Creation timestamp. |
| `b1_config_id` | bigint | yes |  | FK -> strategy_configs.(id, strategy_code) [composite] | Configuration of baseline B1. |
| `b1_code` | strategy_code | no | nearest_feasible | FK -> strategy_configs.(id, strategy_code) [composite] | Pinned to nearest_feasible, so the B1 slot cannot hold another strategy. Values: nearest_feasible, pick_frequency, weighted_score. |
| `b2_config_id` | bigint | yes |  | FK -> strategy_configs.(id, strategy_code) [composite] | Configuration of baseline B2. |
| `b2_code` | strategy_code | no | pick_frequency | FK -> strategy_configs.(id, strategy_code) [composite] | Pinned to pick_frequency. Values: nearest_feasible, pick_frequency, weighted_score. |
| `optimized_config_id` | bigint | yes |  | FK -> strategy_configs.(id, strategy_code) [composite] | Configuration of the optimized strategy. |
| `optimized_code` | strategy_code | no | weighted_score | FK -> strategy_configs.(id, strategy_code) [composite] | Pinned to weighted_score. Values: nearest_feasible, pick_frequency, weighted_score. |
| `locked_at` | timestamptz | yes |  |  | Time of the irreversible lock: protocol, scenarios and strategies are fixed and validation runs are allowed. |
| `outcome_level` | outcome_level | yes |  |  | achieved, partially_achieved or not_achieved (Table 2.15); only after the lock. Values: achieved, partially_achieved, not_achieved. |

### `scenarios`

Simulation scenarios of an experiment: kind, purpose (tuning or validation), seed and generator parameters. Each scenario owns one simulation warehouse.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Surrogate primary key (identity). |
| `experiment_id` | bigint | no |  | FK -> experiments.id | Experiment the scenario belongs to. |
| `warehouse_id` | bigint | no |  | FK -> warehouses.id; UNIQUE | Simulation warehouse owned by the scenario (unique). |
| `name` | varchar(128) | no |  |  | Scenario name, unique within the experiment. |
| `kind` | scenario_kind | no |  |  | ordinary, high_utilization or short_shelf_life. Values: ordinary, high_utilization, short_shelf_life. |
| `purpose` | scenario_purpose | no |  |  | tuning (before the lock) or validation (final runs; results are reported on these only). Values: tuning, validation. |
| `seed` | bigint | no |  |  | Generator seed; unique per experiment, so a seed cannot be both tuning and validation. |
| `generator_version` | varchar(32) | no |  |  | Version of the data generator. |
| `generator_params` | jsonb | no |  |  | JSON parameters of the generator. |

### `sim_runs`

Runs of a scenario: status, validity and the simulator version.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Surrogate primary key (identity). |
| `scenario_id` | bigint | no |  | FK -> scenarios.(id, warehouse_id) [composite] | Scenario that was run. |
| `warehouse_id` | bigint | no |  | FK -> scenarios.(id, warehouse_id) [composite] | Scenario warehouse (copy enforced by a composite foreign key). |
| `status` | run_status | no | queued |  | queued, running, completed or failed. Values: queued, running, completed, failed. |
| `is_valid` | boolean | yes |  |  | False if KPI4 is not 0 for any strategy (the run is invalid); set on completion. |
| `sim_code_version` | varchar(64) | no |  |  | Git commit of the simulator, for reproducibility (NFR8). |
| `created_at` | timestamptz | no | now() |  | Creation timestamp. |
| `started_at` | timestamptz | yes |  |  | Start time. |
| `finished_at` | timestamptz | yes |  |  | Finish time; required when completed. |
| `error_message` | text | yes |  |  | Error text of a failed run. |

### `sim_operations`

Raw simulated operations (putaway, relocation, rejection) of each strategy in a run. The data behind KPI1 and KPI2.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Surrogate primary key (identity). |
| `run_id` | bigint | no |  | FK -> sim_runs.(id, warehouse_id) [composite] | Run the operation belongs to. |
| `warehouse_id` | bigint | no |  | FK -> sim_runs.(id, warehouse_id) [composite]; FK -> batches.(id, warehouse_id) [composite]; FK -> locations.(id, warehouse_id) [composite]; FK -> locations.(id, warehouse_id) [composite] | Scenario warehouse. |
| `strategy_config_id` | bigint | no |  | FK -> strategy_configs.id | Strategy that produced the operation. |
| `seq_no` | integer | no |  |  | Order of the operation within the run and strategy. |
| `operation_type` | sim_operation_type | no |  |  | putaway, relocation or rejected. Values: putaway, relocation, rejected. |
| `batch_id` | bigint | no |  | FK -> batches.(id, warehouse_id) [composite] | Batch of the operation. |
| `from_location_id` | bigint | yes |  | FK -> locations.(id, warehouse_id) [composite] | Source location; relocation only. |
| `to_location_id` | bigint | yes |  | FK -> locations.(id, warehouse_id) [composite] | Target location; NULL for rejected. |
| `distance_m` | numeric(10,2) | yes |  |  | Putaway only: movement distance (receiving zone to location plus location to destination zone), KPI1. |
| `checkpoint_no` | integer | yes |  |  | Required for relocations: checkpoint at which the relocation rule fired. |

### `sim_run_metrics`

Metric values per run, strategy and checkpoint: KPI1-KPI4, SI1, SI2, rejections and run time.

| Column | Type | Null | Default | Key | Description |
|---|---|---|---|---|---|
| `run_id` | bigint | no |  | PK; FK -> sim_runs.id | Run. |
| `strategy_config_id` | bigint | no |  | PK; FK -> strategy_configs.id | Strategy the value belongs to. |
| `metric_code` | metric_code | no |  | PK | Which metric (KPI1-KPI4, SI1, SI2, rejections, runtime). Values: kpi1_avg_distance, kpi2_additional_movements, kpi3_utilization, kpi4_violations, si1_short_life_in_fast_zone, si2_explained_share, rejections, runtime_ms. |
| `checkpoint_no` | integer | no | -1 | PK | -1 = final value at the end of the scenario; 0 = initial state; 1..n = checkpoints. |
| `value` | numeric(18,6) | no |  |  | Metric value. |

## Enums

| Type | Value | Meaning |
|---|---|---|
| `warehouse_kind` | `live` | The single demo (live) warehouse; unique. |
| `warehouse_kind` | `simulation` | A warehouse owned by one scenario; stateless. |
| `zone_type` | `receiving` | Goods arrive here; exactly one per warehouse. |
| `zone_type` | `picking` | Picking destination zone; exactly one per warehouse. |
| `zone_type` | `shipping` | Shipping destination zone; exactly one per warehouse. |
| `zone_type` | `storage` | Zone with storage locations; any number. |
| `access_tier` | `fast` | Fast-access zone: closest tier, class A in B2 and short-dated batches. |
| `access_tier` | `middle` | Middle zone; class B in B2. |
| `access_tier` | `remaining` | All other storage; class C in B2. |
| `batch_status` | `awaiting_putaway` | Registered, not placed yet. |
| `batch_status` | `placed` | Putaway confirmed; location and time are set. |
| `outbound_flow` | `picking` | The product leaves through the picking zone. |
| `outbound_flow` | `shipping` | The product leaves through the shipping zone. |
| `user_role` | `operator` | Warehouse Operator. |
| `user_role` | `manager` | Warehouse Manager. |
| `user_role` | `planner` | Inventory / Warehouse Planner. |
| `user_role` | `business_manager` | Business / Operations Manager (view only). |
| `user_role` | `admin` | System Administrator. |
| `strategy_code` | `nearest_feasible` | B1: the feasible location nearest to the receiving zone. |
| `strategy_code` | `pick_frequency` | B2: pick-frequency (ABC zones) heuristic. |
| `strategy_code` | `weighted_score` | The optimized multi-criteria weighted ranking. |
| `import_entity` | `zones` | CSV with zones. |
| `import_entity` | `locations` | CSV with locations and distances. |
| `import_entity` | `compat_groups` | CSV with compatibility groups. |
| `import_entity` | `compat_conflicts` | CSV with incompatible group pairs. |
| `import_entity` | `products` | CSV with the product catalog. |
| `import_entity` | `batches` | CSV with batches. |
| `import_entity` | `outbound_history` | CSV with pick history. |
| `job_status` | `pending` | Created, not started. |
| `job_status` | `running` | In progress. |
| `job_status` | `completed` | Finished. |
| `job_status` | `failed` | Stopped by an error. |
| `movement_type` | `putaway` | First placement of a batch. |
| `movement_type` | `relocation` | Move from one location to another. |
| `movement_type` | `import` | Initial load through CSV import. |
| `abc_class` | `A` | Fast movers: the first cumulative share of picks (default 80%). |
| `abc_class` | `B` | Medium movers (next 15% by default). |
| `abc_class` | `C` | Slow movers and products without history. |
| `xyz_class` | `X` | Stable demand (CV up to 0.5). |
| `xyz_class` | `Y` | Variable demand (CV up to 1.0). |
| `xyz_class` | `Z` | Irregular demand (CV above 1.0). |
| `scenario_kind` | `ordinary` | Ordinary utilization. |
| `scenario_kind` | `high_utilization` | Incoming volume close to usable capacity (needed for KPI3). |
| `scenario_kind` | `short_shelf_life` | Large share of batches with limited shelf life (needed for SI1). |
| `scenario_purpose` | `tuning` | Used only to tune weights and thresholds before the lock. |
| `scenario_purpose` | `validation` | Used for the final runs; results are reported on these only. |
| `run_status` | `queued` | Created, waiting. |
| `run_status` | `running` | In progress. |
| `run_status` | `completed` | Finished; final for validation runs. |
| `run_status` | `failed` | Stopped by an error; final for validation runs. |
| `outcome_level` | `achieved` | KPI1-KPI3 improved over both baselines, KPI4 = 0, no metric worse (Table 2.15). |
| `outcome_level` | `partially_achieved` | At least one of KPI1-KPI3 improved over B1 and KPI4 = 0. |
| `outcome_level` | `not_achieved` | All other cases; results and reasons are documented. |
| `metric_code` | `kpi1_avg_distance` | KPI1: average movement distance per putaway. |
| `metric_code` | `kpi2_additional_movements` | KPI2: number of relocations under the fixed rule. |
| `metric_code` | `kpi3_utilization` | KPI3: usable capacity utilization, %. |
| `metric_code` | `kpi4_violations` | KPI4: hard-constraint violations (control metric, must be 0). |
| `metric_code` | `si1_short_life_in_fast_zone` | SI1: share of short-shelf-life batches in the fast-access zone. |
| `metric_code` | `si2_explained_share` | SI2: share of recommendations with an explanation. |
| `metric_code` | `rejections` | Number of putaway rejections. |
| `metric_code` | `runtime_ms` | Run time in ms (non-deterministic; excluded from the reproducibility check). |
| `sim_operation_type` | `putaway` | Simulated placement of an arriving batch. |
| `sim_operation_type` | `relocation` | Simulated relocation under the KPI2 rule. |
| `sim_operation_type` | `rejected` | Putaway rejected: no feasible location. |

## Relationships

| Child | Columns | Parent | Columns | ON DELETE |
|---|---|---|---|---|
| `zones` | warehouse_id | `warehouses` | id | NO ACTION |
| `locations` | warehouse_id | `warehouses` | id | NO ACTION |
| `locations` | zone_id, warehouse_id, zone_type | `zones` | id, warehouse_id, zone_type | NO ACTION |
| `compat_groups` | warehouse_id | `warehouses` | id | NO ACTION |
| `compat_conflicts` | group_a_id, warehouse_id | `compat_groups` | id, warehouse_id | NO ACTION |
| `compat_conflicts` | group_b_id, warehouse_id | `compat_groups` | id, warehouse_id | NO ACTION |
| `products` | warehouse_id | `warehouses` | id | NO ACTION |
| `products` | compat_group_id, warehouse_id | `compat_groups` | id, warehouse_id | NO ACTION |
| `batches` | warehouse_id | `warehouses` | id | NO ACTION |
| `batches` | product_id, warehouse_id | `products` | id, warehouse_id | NO ACTION |
| `batches` | current_location_id, warehouse_id | `locations` | id, warehouse_id | NO ACTION |
| `batches` | created_by | `users` | id | NO ACTION |
| `outbound_history` | product_id | `products` | id | NO ACTION |
| `stock_movements` | batch_id, warehouse_id | `batches` | id, warehouse_id | NO ACTION |
| `stock_movements` | from_location_id, warehouse_id | `locations` | id, warehouse_id | NO ACTION |
| `stock_movements` | to_location_id, warehouse_id | `locations` | id, warehouse_id | NO ACTION |
| `stock_movements` | recommendation_item_id, warehouse_id | `recommendation_items` | id, warehouse_id | NO ACTION |
| `stock_movements` | performed_by | `users` | id | NO ACTION |
| `import_jobs` | warehouse_id | `warehouses` | id | NO ACTION |
| `import_jobs` | created_by | `users` | id | NO ACTION |
| `classification_runs` | warehouse_id | `warehouses` | id | NO ACTION |
| `product_classes` | classification_run_id, warehouse_id | `classification_runs` | id, warehouse_id | NO ACTION |
| `product_classes` | product_id, warehouse_id | `products` | id, warehouse_id | NO ACTION |
| `recommendations` | batch_id, warehouse_id | `batches` | id, warehouse_id | NO ACTION |
| `recommendations` | strategy_config_id | `strategy_configs` | id | NO ACTION |
| `recommendations` | requested_by | `users` | id | NO ACTION |
| `recommendation_items` | recommendation_id, warehouse_id | `recommendations` | id, warehouse_id | NO ACTION |
| `recommendation_items` | location_id, warehouse_id | `locations` | id, warehouse_id | NO ACTION |
| `experiments` | b1_config_id, b1_code | `strategy_configs` | id, strategy_code | NO ACTION |
| `experiments` | b2_config_id, b2_code | `strategy_configs` | id, strategy_code | NO ACTION |
| `experiments` | optimized_config_id, optimized_code | `strategy_configs` | id, strategy_code | NO ACTION |
| `scenarios` | experiment_id | `experiments` | id | CASCADE |
| `scenarios` | warehouse_id | `warehouses` | id | NO ACTION |
| `sim_runs` | scenario_id, warehouse_id | `scenarios` | id, warehouse_id | CASCADE |
| `sim_operations` | run_id, warehouse_id | `sim_runs` | id, warehouse_id | CASCADE |
| `sim_operations` | strategy_config_id | `strategy_configs` | id | NO ACTION |
| `sim_operations` | batch_id, warehouse_id | `batches` | id, warehouse_id | NO ACTION |
| `sim_operations` | from_location_id, warehouse_id | `locations` | id, warehouse_id | NO ACTION |
| `sim_operations` | to_location_id, warehouse_id | `locations` | id, warehouse_id | NO ACTION |
| `sim_run_metrics` | run_id | `sim_runs` | id | CASCADE |
| `sim_run_metrics` | strategy_config_id | `strategy_configs` | id | NO ACTION |

## Partial unique indexes

| Table | Index | Purpose |
|---|---|---|
| `batches` | `uq_batches_arrival_seq` | Arrival order is unique within a warehouse (same sequence for all strategies, NFR8). |
| `sim_operations` | `uq_sim_ops_one_placement_per_batch` | In one run and strategy a batch has at most one putaway or rejection. |
| `strategy_configs` | `uq_strategy_configs_single_default` | At most one default strategy configuration. |
| `warehouses` | `uq_single_live_warehouse` | Exactly one live warehouse (AC4). |
| `zones` | `uq_zones_one_per_special_type` | One receiving, one picking and one shipping zone per warehouse (AC4). |

## CHECK rules

| Table | Constraint | Rule | BA |
|---|---|---|---|
| `batches` | `chk_batches_expiry` | Expiry date, if present, is later than the receiving date. | US1.4 |
| `batches` | `chk_batches_positive` | Quantity, volume and weight are greater than 0. | US1.4 |
| `batches` | `chk_batches_status_location` | A placed batch has a location and a placement time; a batch awaiting putaway has neither. | US1.4, US5.3 |
| `classification_runs` | `chk_class_runs_cv` | 0 < cv_x_max < cv_y_max. | US2.2 |
| `classification_runs` | `chk_class_runs_shares` | ABC shares are positive and their sum is below 1. | US2.1 |
| `classification_runs` | `chk_class_runs_window` | window_start is not after window_end. | US2.1 |
| `compat_conflicts` | `chk_conflicts_canonical` | A conflicting pair is stored once: group_a_id < group_b_id. | US3.2 |
| `experiments` | `chk_experiments_outcome_after_lock` | outcome_level can be set only after the lock. | Table 2.15 |
| `experiments` | `chk_experiments_pinned_codes` | Slots hold the right strategies: B1 = nearest_feasible, B2 = pick_frequency, optimized = weighted_score. | US4.5, US6.1 |
| `experiments` | `chk_experiments_threshold` | Evaluation threshold, if set, is greater than 0. | SI1, KPI2 |
| `import_jobs` | `chk_import_counts` | Counters are not negative and imported_rows + error_rows <= total_rows. | US1.7 |
| `locations` | `chk_locations_distances` | The three distances are not negative. | US1.1, KPI1 |
| `locations` | `chk_locations_in_storage_zone` | A location always sits in a storage zone (zone_type = storage). | US1.1, AC4 |
| `locations` | `chk_locations_occupancy` | Occupancy is not negative and never above capacity or maximum load. | US1.5, NFR7 |
| `locations` | `chk_locations_positive` | Capacity, maximum load and maximum dimensions are greater than 0. | US1.2 |
| `outbound_history` | `chk_outbound_nonneg` | Quantity and picks_count are greater than 0. | US2.1 |
| `product_classes` | `chk_product_classes_counts` | movement_count >= 0; cumulative_share between 0 and 1. | US2.1 |
| `products` | `chk_products_positive` | Dimensions, unit volume and weight are greater than 0; shelf life, if set, is greater than 0. | US1.3 |
| `products` | `chk_products_unit_volume` | Unit volume does not exceed the volume of the bounding box (length x width x height). | US1.3 |
| `recommendation_items` | `chk_items_explanation` | The explanation is never empty. | US4.4, SI2 |
| `recommendation_items` | `chk_items_rank` | Rank starts from 1. | US4.1 |
| `recommendations` | `chk_recommendations_count` | feasible_count >= 0. | US3.1 |
| `sim_operations` | `chk_sim_ops_seq` | seq_no >= 0. | US6.5 |
| `sim_operations` | `chk_sim_ops_shape` | Field combinations per operation: putaway = no source, target and distance; relocation = source, different target, checkpoint, no distance; rejected = no source, no target, no distance. | US6.5 |
| `sim_run_metrics` | `chk_sim_metrics_checkpoint` | checkpoint_no >= -1 (-1 = final value). | US6.2 |
| `sim_runs` | `chk_sim_runs_completed` | A completed run has a finish time and a validity flag. | US6.1, KPI4 |
| `stock_movements` | `chk_movements_override_reason` | An override must have a non-empty reason. | US5.4 |
| `stock_movements` | `chk_movements_shape` | relocation has a source different from the target; putaway and import have no source. | US1.5 |
| `strategy_configs` | `chk_strategy_top_n` | top_n > 0. | US4.1 |
| `strategy_configs` | `chk_strategy_weights` | weighted_score needs four non-negative weights summing to 1 and a positive low_shelf_life_days; B1 and B2 have none of them. | US4.2, US4.3, US4.5 |
| `zones` | `chk_zones_tiers` | Storage zones have both access tiers; other zones have none. | US1.1, US4.5 |

## Triggers, functions, views

| Type | Name | Applies to | What it does |
|---|---|---|---|
| Trigger | `trg_protect_strategy_config` | strategy_configs, BEFORE UPDATE / DELETE | A frozen configuration is immutable (only top_n and is_default may change) and cannot be unfrozen or deleted; weights of a configuration already used by runs or recommendations cannot change. |
| Trigger | `trg_final_run_sim_runs` | sim_runs, BEFORE UPDATE / DELETE | A finished validation run (completed or failed) is final; a scenario has at most one completed valid validation run. |
| Trigger | `trg_final_run_sim_operations` | sim_operations, BEFORE INSERT / UPDATE / DELETE | Operations of a finished validation run cannot be added, changed or deleted. |
| Trigger | `trg_final_run_sim_run_metrics` | sim_run_metrics, BEFORE INSERT / UPDATE / DELETE | Metrics of a finished validation run cannot be added, changed or deleted. |
| Trigger | `trg_protect_scenario_with_final_run` | scenarios, BEFORE DELETE | A scenario with a final validation run cannot be deleted, also not by a cascade from the experiment or warehouse. |
| Function | `protect_strategy_config()` | trigger function | Implements trg_protect_strategy_config. |
| Function | `protect_final_validation_run()` | trigger function | Implements the three trg_final_run_* triggers. |
| Function | `protect_scenario_with_final_run()` | trigger function | Implements trg_protect_scenario_with_final_run. |
| Function | `anonymize_user(p_user_id bigint)` | returns void | GDPR erasure: replaces login and full name, clears the password hash, deactivates the user and sets anonymized_at. The row stays for the audit trail. |
| Function | `purge_simulation_warehouse(p_warehouse_id bigint)` | returns void | Deletes all data of a simulation warehouse; refuses live warehouses and anything the protection triggers guard. |
| View | `v_location_occupancy_drift` | locations, batches | Consistency check: live locations whose stored occupied volume or weight differs from the sum over placed batches. |
| View | `v_product_destination_zone` | products, zones | Destination zone (picking or shipping) of each product, chosen by outbound_flow (KPI1). |
| View | `v_warehouses_missing_special_zones` | warehouses, zones | Warehouses that lack a receiving, picking or shipping zone. |

## Service-layer rules (not enforced by the database)

| ID | Rule |
|---|---|
| R-S1 | Simulation warehouses are stateless: batches stay awaiting_putaway without a location; locations keep occupied volume and weight at 0. |
| R-S2 | A scenario must use a warehouse of kind simulation. |
| R-S3 | Locking an experiment (irreversible) requires: low_shelf_life_days set; B1, B2 and optimized configurations set and frozen; validation scenarios of all three kinds. |
| R-S4 | After the lock: protocol, threshold, the three configuration fields and the set of scenarios do not change; the experiment is not deleted. |
| R-S5 | Run gate: a tuning run is created only before the lock, a validation run only after it. |
| R-S6 | sim_operations and sim_run_metrics refer only to configurations of the run's experiment. |
| R-S7 | Rules over several rows (compatibility re-check, batch volume and weight calculation, archived products not offered) stay in the putaway service. |
