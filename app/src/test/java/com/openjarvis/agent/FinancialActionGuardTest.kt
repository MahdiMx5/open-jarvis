package com.openjarvis.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialActionGuardTest {
    @Test
    fun blocksTypingInsideBankingApps() {
        assertTrue(
            RiskyActionConfirmation.isFinancialInteractionBlocked(
                Action(Action.TYPE, value = "sample"),
                "com.example.mobilebank"
            )
        )
    }

    @Test
    fun blocksScreenReadingInsideWalletApps() {
        assertTrue(
            RiskyActionConfirmation.isFinancialInteractionBlocked(
                Action(Action.EXTRACT_TEXT),
                "com.example.wallet"
            )
        )
    }

    @Test
    fun allowsLeavingFinancialApps() {
        assertFalse(
            RiskyActionConfirmation.isFinancialInteractionBlocked(
                Action(Action.PRESS_BACK),
                "com.example.wallet"
            )
        )
    }

    @Test
    fun doesNotBlockOrdinaryMessagingApps() {
        assertFalse(
            RiskyActionConfirmation.isFinancialInteractionBlocked(
                Action(Action.TYPE, value = "Hello"),
                "com.whatsapp"
            )
        )
    }
}
