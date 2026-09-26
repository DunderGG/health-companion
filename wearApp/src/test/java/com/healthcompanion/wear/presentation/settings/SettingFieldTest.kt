// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.settings

import com.healthcompanion.core.domain.settings.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingFieldTest {

    @Test
    fun `every option of every field can be stored and read back`() {
        for (field in SettingField.entries) {
            for (value in field.options) {
                assertEquals("$field", value, field.valueIn(field.update(UserSettings.DEFAULT, value)))
            }
        }
    }

    @Test
    fun `the defaults are options, so every stepper starts on a real position`() {
        for (field in SettingField.entries) {
            assertTrue("$field", field.valueIn(UserSettings.DEFAULT) in field.options)
        }
    }

    @Test
    fun `bedtime and wake-up hours never overlap, so the night is never empty`() {
        assertTrue(SettingField.BEDTIME_START.options.none { it in SettingField.BEDTIME_END.options })
    }

    @Test
    fun `bedtime steps past midnight`() {
        val options = SettingField.BEDTIME_START.options
        assertEquals(0, options[options.indexOf(23) + 1])
    }

    @Test
    fun `a value between options maps to the nearest one`() {
        assertEquals(SettingField.STEPS.options.indexOf(6_000), SettingField.STEPS.indexOf(6_100))
    }
}
