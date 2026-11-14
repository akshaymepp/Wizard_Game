package com.akshay.wizardgame

import org.junit.Assert.*
import org.junit.Test

class WizardRulesTest {
    @Test fun firstWizardInTrickWins() {
        val plays = listOf(
            PlayedCard(0, WizardCard(Kind.NUMBER, Suit.RED, 13)),
            PlayedCard(1, WizardCard(Kind.WIZARD)),
            PlayedCard(2, WizardCard(Kind.WIZARD))
        )
        assertEquals(1, WizardEngine.trickWinner(plays, Suit.RED, 0))
    }

    @Test fun trumpBeatsLedSuit() {
        val plays = listOf(
            PlayedCard(0, WizardCard(Kind.NUMBER, Suit.RED, 13)),
            PlayedCard(1, WizardCard(Kind.NUMBER, Suit.BLUE, 2))
        )
        assertEquals(1, WizardEngine.trickWinner(plays, Suit.BLUE, 0))
    }

    @Test fun ledSuitBeatsOffSuitWhenNoTrump() {
        val plays = listOf(
            PlayedCard(0, WizardCard(Kind.NUMBER, Suit.RED, 8)),
            PlayedCard(1, WizardCard(Kind.NUMBER, Suit.GREEN, 13))
        )
        assertEquals(0, WizardEngine.trickWinner(plays, null, 0))
    }

    @Test fun allJestersMeansFirstJesterWins() {
        val plays = listOf(
            PlayedCard(0, WizardCard(Kind.JESTER)),
            PlayedCard(1, WizardCard(Kind.JESTER))
        )
        assertEquals(0, WizardEngine.trickWinner(plays, Suit.BLUE, 0))
    }

    @Test fun jesterDoesNotBeatAnyNumberCard() {
        val plays = listOf(
            PlayedCard(0, WizardCard(Kind.JESTER)),
            PlayedCard(1, WizardCard(Kind.NUMBER, Suit.GREEN, 1))
        )
        assertEquals(1, WizardEngine.trickWinner(plays, null, 0))
    }

    @Test fun firstBidStartsToTheDealersLeft() {
        val game = WizardEngine(listOf("Human" to false, "CPU 1" to true, "CPU 2" to true))
        assertEquals(3, game.state.players.size)
        assertEquals((game.state.dealerIndex + 1) % game.state.players.size, game.state.bidTurnIndex)
    }
}
