package com.akshay.wizardgame

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class WizardEngineTest {
    @Test fun deckContainsStandardSixtyCardsAndRanks() {
        val deck = WizardCard.fullDeck()
        assertEquals(60, deck.size)
        assertEquals(52, deck.count { it.kind == Kind.NUMBER })
        assertEquals(4, deck.count { it.kind == Kind.WIZARD })
        assertEquals(4, deck.count { it.kind == Kind.JESTER })
        assertEquals((2..14).toSet(), deck.filter { it.kind == Kind.NUMBER && it.suit == Suit.RED }.map { it.value }.toSet())
    }

    @Test fun supportedPlayerCountsHaveCorrectRoundLimits() {
        val expected = mapOf(3 to 20, 4 to 15, 5 to 12, 6 to 10)
        expected.forEach { (count, rounds) ->
            val game = WizardEngine((0 until count).map { "P$it" to (it != 0) }, Random(count))
            assertEquals(rounds, game.state.maxRounds)
            assertEquals(1, game.state.cardsPerPlayer)
        }
    }

    @Test fun rejectsUnsupportedPlayerCounts() {
        assertThrows(IllegalArgumentException::class.java) {
            WizardEngine(listOf("A" to false, "B" to true), Random(1))
        }
    }

    @Test fun biddingStartsLeftOfDealerAndIllegalFinalBidDoesNotMutateState() {
        val game = WizardEngine((0..2).map { "P$it" to false }, Random(5))
        val field = WizardEngine::class.java.getDeclaredField("state")
        field.isAccessible = true
        field.set(game, game.state.copy(round = 3))
        if (game.state.trumpIsWizard) game.chooseTrump(null)
        val first = (game.state.dealerIndex + 1) % 3
        assertEquals(first, game.state.bidTurnIndex)
        game.submitBid(first, 1)
        val second = game.state.bidTurnIndex
        game.submitBid(second, 1)
        val last = game.state.bidTurnIndex
        assertEquals(listOf(0, 2, 3), game.legalBidsFor(last))
        assertThrows(IllegalArgumentException::class.java) { game.submitBid(last, 1) }
        assertNull(game.state.players[last].bid)
        assertEquals(2, game.state.bidTotal)
    }

    @Test fun aiBidsObeyHouseRuleAndClockwiseOrder() {
        val game = WizardEngine(listOf("Human" to false, "CPU 1" to true, "CPU 2" to true), Random(19))
        if (game.state.trumpIsWizard) game.chooseTrump(null)
        game.submitBid(game.state.bidTurnIndex, 0)
        game.submitRemainingAiBids()
        assertEquals(0, game.state.bidTurnIndex)
        val humanBid = game.legalBidsFor(0).first()
        game.submitBid(0, humanBid)
        assertTrue(game.state.bidsLocked)
        assertNotEquals(game.state.round, game.state.bidTotal)
    }

    @Test fun followSuitIsRequiredButWizardsAndJestersRemainPlayable() {
        val game = WizardEngine((0..2).map { "P$it" to false }, Random(7))
        val hands = game.state.players.mapIndexed { i, p ->
            when (i) {
                game.state.leaderIndex -> p.copy(hand = listOf(WizardCard(Kind.NUMBER, Suit.RED, 2), WizardCard(Kind.JESTER)))
                else -> p.copy(hand = listOf(WizardCard(Kind.NUMBER, Suit.RED, 14), WizardCard(Kind.NUMBER, Suit.BLUE, 14), WizardCard(Kind.WIZARD), WizardCard(Kind.JESTER)))
            }
        }
        replacePlayers(game, hands)
        lockZeroBids(game)
        val lead = game.state.currentPlayerIndex
        game.playCard(lead, WizardCard(Kind.NUMBER, Suit.RED, 2))
        val follower = game.state.currentPlayerIndex
        assertThrows(IllegalArgumentException::class.java) {
            game.playCard(follower, WizardCard(Kind.NUMBER, Suit.BLUE, 14))
        }
        assertEquals(1, game.state.trick.size)
        val legal = game.legalCards(follower)
        assertTrue(legal.contains(WizardCard(Kind.NUMBER, Suit.RED, 14)))
        assertTrue(legal.contains(WizardCard(Kind.WIZARD)))
        assertTrue(legal.contains(WizardCard(Kind.JESTER)))
        assertFalse(legal.contains(WizardCard(Kind.NUMBER, Suit.BLUE, 14)))
    }

    @Test fun firstJesterAllowsAnyUntilNormalSuitAppearsAndLaterWizardDoesNotEraseLead() {
        val game = WizardEngine((0..2).map { "P$it" to false }, Random(8))
        val start = game.state.currentPlayerIndex
        val third = (start + 2) % 3
        val thirdHand = listOf(WizardCard(Kind.NUMBER, Suit.GREEN, 2), WizardCard(Kind.NUMBER, Suit.RED, 10), WizardCard(Kind.WIZARD))
        replacePlayers(game, game.state.players.mapIndexed { i, p ->
            when (i) {
                start -> p.copy(hand = listOf(WizardCard(Kind.JESTER)))
                (start + 1) % 3 -> p.copy(hand = listOf(WizardCard(Kind.NUMBER, Suit.GREEN, 5)))
                else -> p.copy(hand = thirdHand)
            }
        })
        lockZeroBids(game)
        game.playCard(start, WizardCard(Kind.JESTER))
        game.playCard(game.state.currentPlayerIndex, WizardCard(Kind.NUMBER, Suit.GREEN, 5))
        val next = game.state.currentPlayerIndex
        assertEquals(third, next)
        assertEquals(setOf(thirdHand[0], thirdHand[2]), game.legalCards(next).toSet())
    }

    @Test fun wizardSecondAfterJesterRemovesSuitFollowingForTheWholeTrick() {
        val game = WizardEngine((0..3).map { "P$it" to false }, Random(17))
        val start = game.state.currentPlayerIndex
        val third = (start + 2) % 4
        val fourth = (start + 3) % 4
        val red = WizardCard(Kind.NUMBER, Suit.RED, 2)
        val blue = WizardCard(Kind.NUMBER, Suit.BLUE, 14)
        replacePlayers(game, game.state.players.mapIndexed { i, p ->
            when (i) {
                start -> p.copy(hand = listOf(WizardCard(Kind.JESTER)))
                (start + 1) % 4 -> p.copy(hand = listOf(WizardCard(Kind.WIZARD)))
                third -> p.copy(hand = listOf(red, blue))
                else -> p.copy(hand = listOf(WizardCard(Kind.NUMBER, Suit.GREEN, 2), WizardCard(Kind.NUMBER, Suit.YELLOW, 14)))
            }
        })
        lockZeroBids(game)
        game.playCard(start, WizardCard(Kind.JESTER))
        game.playCard(game.state.currentPlayerIndex, WizardCard(Kind.WIZARD))
        assertEquals(setOf(red, blue), game.legalCards(third).toSet())
        game.playCard(third, red)
        assertEquals(2, game.legalCards(fourth).size)
    }

    @Test fun aiMustFollowSuitWhenItHasTheLedSuit() {
        val game = WizardEngine(listOf("Human" to false, "CPU 1" to true, "CPU 2" to true), Random(27))
        val lead = 1
        val hands = listOf(
            listOf(WizardCard(Kind.JESTER)),
            listOf(WizardCard(Kind.NUMBER, Suit.RED, 2)),
            listOf(WizardCard(Kind.NUMBER, Suit.BLUE, 14), WizardCard(Kind.NUMBER, Suit.RED, 14))
        )
        replacePlayers(game, game.state.players.mapIndexed { i, p -> p.copy(hand = hands[i], bid = 0) })
        val field = WizardEngine::class.java.getDeclaredField("state")
        field.isAccessible = true
        field.set(game, game.state.copy(leaderIndex = lead, bidTurnIndex = 0, bidsLocked = true, trumpChosen = true, trumpIsWizard = false, trump = null))
        game.playAiTurn()
        game.playAiTurn()
        assertEquals(Suit.RED, game.state.trick[1].card.suit)
    }

    @Test fun wizardUpcardRequiresAnExplicitDealerChoice() {
        val game = (1..500).asSequence().map { WizardEngine((0..2).map { i -> "P$i" to (i != 0) }, Random(it)) }
            .first { it.state.trumpIsWizard }
        assertFalse(game.state.trumpChosen)
        game.chooseTrump(Suit.BLUE)
        assertTrue(game.state.trumpChosen)
        assertEquals(Suit.BLUE, game.state.trump)
    }

    @Test fun finalThreePlayerRoundHasNoUpcardOrTrump() {
        val game = WizardEngine((0..2).map { "P$it" to (it != 0) }, Random(41))
        val field = WizardEngine::class.java.getDeclaredField("state")
        field.isAccessible = true
        field.set(game, game.state.copy(round = 20))
        game.startRound()
        assertEquals(20, game.state.cardsPerPlayer)
        assertNull(game.state.trumpUpcard)
        assertNull(game.state.trump)
        assertTrue(game.state.trumpChosen)
    }

    @Test fun fullGamesFinishAtTheCorrectRoundForThreeThroughSixPlayers() {
        for (count in 3..6) {
            val game = WizardEngine((0 until count).map { "P$it" to false }, Random(100 + count))
            while (!game.state.finished) {
                if (game.state.trumpIsWizard && !game.state.trumpChosen) game.chooseTrump(null)
                while (!game.state.bidsLocked) {
                    val bidder = game.state.bidTurnIndex
                    val options = game.legalBidsFor(bidder)
                    game.submitBid(bidder, if (0 in options) 0 else options.first())
                }
                while (game.state.lastSummary == null) {
                    val player = game.state.currentPlayerIndex
                    game.playCard(player, game.legalCards(player).first())
                    if (game.state.trick.size == count) game.resolveCompletedTrick()
                }
                if (!game.state.finished) {
                    val oldDealer = game.state.dealerIndex
                    game.nextRound()
                    assertEquals((oldDealer + 1) % count, game.state.dealerIndex)
                    assertEquals((game.state.dealerIndex + 1) % count, game.state.bidTurnIndex)
                }
            }
            assertEquals(60 / count, game.state.round)
            assertEquals(60 / count, game.state.roundHistory.size)
            game.state.players.forEachIndexed { index, player ->
                assertEquals(game.state.roundHistory.sumOf { it.rows[index].roundPoints }, player.totalScore)
            }
            game.startNewGame()
            assertEquals(1, game.state.round)
            assertTrue(game.state.roundHistory.isEmpty())
            assertTrue(game.state.players.all { it.totalScore == 0 })
        }
    }

    @Test fun trickResolutionAwardsTricksAndCumulativeScoresAcrossRounds() {
        val game = WizardEngine((0..2).map { "P$it" to false }, Random(31))
        forceOneCardRound(game)
        val lead = game.state.currentPlayerIndex
        val ordered = listOf(
            WizardCard(Kind.NUMBER, Suit.RED, 14),
            WizardCard(Kind.NUMBER, Suit.RED, 13),
            WizardCard(Kind.JESTER)
        )
        replacePlayers(game, game.state.players.mapIndexed { i, p ->
            val order = (0..2).first { offset -> (lead + offset) % 3 == i }
            p.copy(hand = listOf(ordered[order]))
        })
        ordered.forEachIndexed { offset, card -> game.playCard((lead + offset) % 3, card) }
        assertEquals(3, game.state.trick.size)
        val winner = game.currentTrickWinner()
        game.resolveCompletedTrick()
        assertEquals(1, game.state.players[winner!!].tricksWon)
        assertNotNull(game.state.lastSummary)
        assertEquals(game.state.players[winner].roundPoints, game.state.players[winner].totalScore)
        assertEquals(1, game.state.roundHistory.size)
    }

    @Test fun scoringUsesExactBidBonusAndPerTrickPenalty() {
        assertEquals(40, WizardScoring.points(bid = 2, tricksWon = 2))
        assertEquals(-10, WizardScoring.points(bid = 2, tricksWon = 3))
        assertEquals(-20, WizardScoring.points(bid = 2, tricksWon = 0))
    }

    private fun lockZeroBids(game: WizardEngine) {
        if (game.state.trumpIsWizard && !game.state.trumpChosen) game.chooseTrump(null)
        while (!game.state.bidsLocked) game.submitBid(game.state.bidTurnIndex, 0)
    }

    private fun replacePlayers(game: WizardEngine, players: List<WizardPlayer>) {
        val field = WizardEngine::class.java.getDeclaredField("state")
        field.isAccessible = true
        field.set(game, game.state.copy(players = players))
    }

    private fun forceOneCardRound(game: WizardEngine) {
        val field = WizardEngine::class.java.getDeclaredField("state")
        field.isAccessible = true
        field.set(game, game.state.copy(players = game.state.players.map { it.copy(bid = 0, hand = listOf(WizardCard(Kind.JESTER))) }, bidsLocked = true, trumpChosen = true, trumpIsWizard = false, trump = null))
    }
}
