package io.github.peanutpersimmon.rilo.ui.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationRowActionTest {
    @Test fun permissionIsRequestedOnceBeforeSettingsFallback() {
        assertEquals(NotificationRowAction.REQUEST_PERMISSION,
            notificationRowAction(33, permissionGranted = false, requestedBefore = false))
        assertEquals(NotificationRowAction.OPEN_SYSTEM_SETTINGS,
            notificationRowAction(33, permissionGranted = false, requestedBefore = true))
    }

    @Test fun alreadyGrantedAndOlderAndroidOpenSystemSettings() {
        assertEquals(NotificationRowAction.OPEN_SYSTEM_SETTINGS,
            notificationRowAction(33, permissionGranted = true, requestedBefore = false))
        assertEquals(NotificationRowAction.OPEN_SYSTEM_SETTINGS,
            notificationRowAction(32, permissionGranted = false, requestedBefore = false))
    }

    @Test fun exactAlarmRowOpensSpecialAccessWhetherGrantedOrNot() {
        assertEquals(ExactAlarmRowAction.SPECIAL_ACCESS, exactAlarmRowAction(36, false))
        assertEquals(ExactAlarmRowAction.SPECIAL_ACCESS, exactAlarmRowAction(36, true))
        assertEquals(ExactAlarmRowAction.APP_DETAILS, exactAlarmRowAction(30, true))
    }
}
