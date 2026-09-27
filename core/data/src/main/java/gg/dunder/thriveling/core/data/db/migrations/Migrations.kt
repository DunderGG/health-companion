// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.data.db.migrations

import androidx.room.migration.Migration

/**
 * Every schema migration for [gg.dunder.thriveling.core.data.db.CompanionDatabase], in version order.
 *
 * Release builds have **no destructive fallback**: any version bump without a matching migration
 * here crashes on open instead of silently deleting the user's pet. To change the schema:
 * 1. Bump `version` in `@Database` and build — Room exports `core/data/schemas/.../<version>.json`.
 * 2. Add a `Migration(old, new)` (or an `@AutoMigration`) and register it below.
 * 3. Extend `CompanionDatabaseMigrationTest` to migrate from the previous version and commit the new schema JSON.
 *
 * Schema version history:
 * - **1**: `pets` table (initial release schema).
 * - **2**: adds `habit_events` (+ index on `timestampMillis`) via `@AutoMigration(from = 1, to = 2)` declared on
 *   the `@Database` annotation (AR-3). Auto-migrations are not listed here; only hand-written ones are.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf()
