package com.akshay.wizardgame

import kotlin.random.Random

enum class Suit { RED, BLUE, GREEN, YELLOW }
enum class Kind { NUMBER, JESTER, WIZARD }

data class WizardCard(val kind: Kind, val suit: Suit? = null, val value: Int = 0) {
    val label: String get() = when (kind) {
        Kind.WIZARD -> "W"
        Kind.JESTER -> "J"
        Kind.NUMBER -> "${suit!!.name.first()}$value"
    }
    companion object {
        fun fullDeck(): List<WizardCard> =
            Suit.values().flatMap { suit -> (2..14).map { WizardCard(Kind.NUMBER, suit, it) } } +
            List(4) { WizardCard(Kind.WIZARD) } + List(4) { WizardCard(Kind.JESTER) }
    }
}

data class WizardPlayer(
    val name: String,
    val isAi: Boolean = false,
    val hand: List<WizardCard> = emptyList(),
    val bid: Int? = null,
    val tricksWon: Int = 0,
    val totalScore: Int = 0,
    val roundPoints: Int = 0
)

data class PlayedCard(val playerIndex: Int, val card: WizardCard)
data class RoundSummary(val round: Int, val rows: List<WizardPlayer>, val winnerIndex: Int?)
data class WizardGameState(
    val players: List<WizardPlayer>,
    val round: Int = 1,
    val dealerIndex: Int = 0,
    val leaderIndex: Int = 1,
    val bidTurnIndex: Int = 1,
    val trump: Suit? = null,
    val trumpIsWizard: Boolean = false,
    val trumpChosen: Boolean = true,
    val trumpUpcard: WizardCard? = null,
    val bidsLocked: Boolean = false,
    val trick: List<PlayedCard> = emptyList(),
    val completedTricks: Int = 0,
    val lastSummary: RoundSummary? = null,
    val roundHistory: List<RoundSummary> = emptyList(),
    val finished: Boolean = false
) {
    val cardsPerPlayer: Int get() = round
    val maxRounds: Int get() = 60 / players.size
    val currentPlayerIndex: Int get() = (leaderIndex + trick.size) % players.size
    val bidTotal: Int get() = players.sumOf { it.bid ?: 0 }
}

class WizardEngine(
    names: List<Pair<String, Boolean>>,
    private val random: Random = Random.Default,
    private val aiLevel: AiLevel = AiLevel.MEDIUM
) {
    enum class AiLevel { EASY, MEDIUM, HARD }
    private var deck: MutableList<WizardCard> = mutableListOf()
    var state: WizardGameState

    init {
        require(names.size in 3..6) { "Wizard supports 3 to 6 players." }
        require(names.count { !it.second } >= 1) { "At least one human player is required." }
        state = WizardGameState(names.map { WizardPlayer(it.first, it.second) })
        startRound()
    }

    fun startRound() {
        check(!state.finished)
        deck = WizardCard.fullDeck().shuffled(random).toMutableList()
        val n = state.round
        val dealt = state.players.mapIndexed { i, p ->
            p.copy(hand = List(n) { deck.removeAt(0) }, bid = null, tricksWon = 0, roundPoints = 0)
        }
        val up = deck.firstOrNull()?.also { deck.removeAt(0) }
        val firstPlayer = (state.dealerIndex + 1) % dealt.size
        state = state.copy(
            players = dealt,
            trump = if (up?.kind == Kind.NUMBER) up.suit else null,
            trumpIsWizard = up?.kind == Kind.WIZARD,
            trumpChosen = up?.kind != Kind.WIZARD,
            trumpUpcard = up,
            bidsLocked = false,
            trick = emptyList(),
            completedTricks = 0,
            leaderIndex = firstPlayer,
            bidTurnIndex = firstPlayer,
            lastSummary = null
        )
    }

    fun submitBid(playerIndex: Int, bid: Int): WizardGameState {
        require(!state.bidsLocked)
        require(state.trumpChosen) { "Trump must be selected before bidding." }
        require(playerIndex in state.players.indices)
        require(playerIndex == state.bidTurnIndex) { "Players must bid in clockwise order, starting left of the dealer." }
        require(state.players[playerIndex].bid == null) { "Player has already bid." }
        require(bid in 0..state.round)
        val totalAfterBid = state.bidTotal + bid
        val isFinalBid = state.players.count { it.bid == null } == 1
        require(!isFinalBid || totalAfterBid != state.round) {
            "Total bids cannot equal the number of tricks in this round."
        }
        val updated = state.players.toMutableList()
        updated[playerIndex] = updated[playerIndex].copy(bid = bid)
        val next = (1..updated.size).map { (playerIndex + it) % updated.size }
            .firstOrNull { updated[it].bid == null }
        state = state.copy(players = updated, bidTurnIndex = next ?: playerIndex, bidsLocked = next == null)
        return state
    }

    /** Admin rule: when the final bid would make the total equal the round, offer legal alternatives. */
    fun legalBidsFor(playerIndex: Int): List<Int> {
        require(playerIndex in state.players.indices)
        require(state.players[playerIndex].bid == null) { "Player has already bid." }
        val fixed = state.players.filterIndexed { i, p -> i != playerIndex }.sumOf { it.bid ?: 0 }
        val isFinalBid = state.players.count { it.bid == null } == 1
        return (0..state.round).filter { !isFinalBid || fixed + it != state.round }
    }

    fun autoBid(playerIndex: Int): Int {
        val p = state.players[playerIndex]
        val estimate = estimateTricks(p.hand)
        val choices = legalBidsFor(playerIndex)
        return choices.minByOrNull { kotlin.math.abs(it - estimate) } ?: 0
    }

    fun submitAiBids() {
        submitRemainingAiBids()
    }



    /** When the turned-up card is a Wizard, the dealer chooses trump (or no-trump). */
    fun chooseTrump(suit: Suit?) {
        require(state.trumpIsWizard) { "Trump selection is only available when a Wizard is turned up." }
        require(!state.trumpChosen) { "Trump has already been selected." }
        state = state.copy(trump = suit, trumpChosen = true)
    }

    /** Complete all outstanding AI bids in turn order. Call after human bids are entered. */
    fun submitRemainingAiBids() {
        while (!state.bidsLocked && state.players[state.bidTurnIndex].isAi) {
            val idx = state.bidTurnIndex
            submitBid(idx, autoBid(idx))
        }
    }

    /** Play consecutive AI turns until a human is next, or the round ends. */
    fun playAiTurns() {
        while (!state.finished && state.lastSummary == null && state.bidsLocked && state.trick.size < state.players.size &&
            state.players[state.currentPlayerIndex].isAi) {
            playAiTurn()
        }
    }

    fun legalCards(playerIndex: Int): List<WizardCard> {
        require(playerIndex == state.currentPlayerIndex)
        val hand = state.players[playerIndex].hand
        if (state.trick.isEmpty() || state.trick.first().card.kind == Kind.WIZARD) return hand
        if (state.trick.first().card.kind == Kind.JESTER && state.trick.getOrNull(1)?.card?.kind == Kind.WIZARD) return hand
        val lead = state.trick.firstOrNull { it.card.kind == Kind.NUMBER }?.card?.suit
            ?: return hand
        val following = hand.filter { it.suit == lead }
        return if (following.isEmpty()) hand else hand.filter { it.suit == lead || it.kind != Kind.NUMBER }
    }

    fun chooseAiCardForCurrentPlayer(): WizardCard {
        val player = state.players[state.currentPlayerIndex]
        require(player.isAi) { "It is not an AI player's turn." }
        return chooseAiCard(legalCards(state.currentPlayerIndex), state.trick, state.trump)
    }

    fun currentTrickWinner(): Int? =
        if (state.trick.isEmpty()) null else trickWinner(state.trick, state.trump, state.leaderIndex)

    fun playCard(playerIndex: Int, card: WizardCard): WizardGameState {
        require(state.trumpChosen) { "The dealer must choose trump before play begins." }
        require(state.bidsLocked) { "All bids must be complete first." }
        require(!state.finished && state.lastSummary == null) { "The round is complete." }
        require(state.trick.size < state.players.size) { "This trick is already complete." }
        require(playerIndex == state.currentPlayerIndex) { "It is not this player's turn." }
        val p = state.players[playerIndex]
        require(card in p.hand)
        require(card in legalCards(playerIndex)) { "Players must follow suit when they can." }
        val newPlayers = state.players.toMutableList()
        newPlayers[playerIndex] = p.copy(hand = p.hand - card)
        val newTrick = state.trick + PlayedCard(playerIndex, card)
        state = state.copy(players = newPlayers, trick = newTrick)
        return state
    }

    fun playAiTurn(cardToPlay: WizardCard? = null): WizardGameState {
        val idx = state.currentPlayerIndex
        val player = state.players[idx]
        require(player.isAi)
        val card = cardToPlay ?: chooseAiCard(legalCards(idx), state.trick, state.trump)
        return playCard(idx, card)
    }

    private fun resolveTrick() {
        val winner = trickWinner(state.trick, state.trump, state.leaderIndex)
        val ps = state.players.toMutableList()
        ps[winner] = ps[winner].copy(tricksWon = ps[winner].tricksWon + 1)
        val done = state.completedTricks + 1
        if (done == state.round) {
            val scored = ps.map { p ->
                val bid = p.bid ?: 0
                val pts = WizardScoring.points(bid, p.tricksWon)
                p.copy(roundPoints = pts, totalScore = p.totalScore + pts)
            }
            val summary = RoundSummary(state.round, scored, winner)
            val end = state.round >= state.maxRounds
            state = state.copy(players = scored, completedTricks = done, trick = emptyList(),
                leaderIndex = winner, lastSummary = summary, roundHistory = state.roundHistory + summary, finished = end)
        } else {
            state = state.copy(completedTricks = done, trick = emptyList(), leaderIndex = winner)
        }
    }

    fun nextRound(): WizardGameState {
        require(state.lastSummary != null)
        require(!state.finished) { "The game is complete. Start a new game instead." }
        state = state.copy(round = state.round + 1, dealerIndex = (state.dealerIndex + 1) % state.players.size)
        startRound()
        return state
    }

    fun startNewGame(): WizardGameState {
        val freshPlayers = state.players.map { it.copy(hand = emptyList(), bid = null, tricksWon = 0, totalScore = 0, roundPoints = 0) }
        state = WizardGameState(players = freshPlayers)
        startRound()
        return state
    }

    fun resolveCompletedTrick(): WizardGameState {
        require(state.trick.size == state.players.size) { "Every player must play before resolving a trick." }
        resolveTrick()
        return state
    }

    private fun estimateTricks(hand: List<WizardCard>): Int {
        // Medium uses card strength plus suit-length control; it is intentionally
        // heuristic rather than claiming perfect information about hidden hands.
        val wizardCount = hand.count { it.kind == Kind.WIZARD }
        val jesterCount = hand.count { it.kind == Kind.JESTER }
        val highCards = hand.count { it.kind == Kind.NUMBER && it.value >= 11 }
        val trumpCards = if (state.trump != null) hand.count { it.suit == state.trump } else 0
        val estimate = wizardCount + highCards * 0.48 + (trumpCards * 0.16) - jesterCount * 0.12
        val noise = when (aiLevel) {
            AiLevel.EASY -> random.nextDouble(-0.9, 0.9)
            AiLevel.MEDIUM -> random.nextDouble(-0.30, 0.30)
            AiLevel.HARD -> random.nextDouble(-0.08, 0.08)
        }
        return (estimate + noise).toInt().coerceIn(0, hand.size)
    }

    private fun chooseAiCard(hand: List<WizardCard>, trick: List<PlayedCard>, trump: Suit?): WizardCard {
        if (trick.isEmpty()) {
            // Medium conserves Wizards; lead a high card in a long suit when possible.
            return when (aiLevel) {
                AiLevel.EASY -> hand.random(random)
                AiLevel.MEDIUM -> hand.minByOrNull { card ->
                    when (card.kind) {
                        Kind.JESTER -> -10
                        Kind.WIZARD -> 1000
                        Kind.NUMBER -> (if (card.suit == trump) 100 else 0) + card.value
                    }
                }!!
                AiLevel.HARD -> hand.minByOrNull { cardStrength(it, trump) }!!
            }
        }

        val incumbentIndex = trickWinner(trick, trump, state.leaderIndex)
        val incumbent = trick.first { it.playerIndex == incumbentIndex }.card
        val leadCard = trick.firstOrNull { it.card.kind != Kind.JESTER }?.card ?: trick.first().card
        val myPlayer = state.players[state.currentPlayerIndex]
        val tricksNeeded = ((myPlayer.bid ?: 0) - myPlayer.tricksWon).coerceAtLeast(0)

        fun wins(card: WizardCard): Boolean {
            val hypothetical = trick + PlayedCard(state.currentPlayerIndex, card)
            return trickWinner(hypothetical, trump, state.leaderIndex) == state.currentPlayerIndex
        }

        val winningCards = hand.filter(::wins)
        val losingCards = hand.filterNot(::wins)
        return when (aiLevel) {
            AiLevel.EASY -> hand.random(random)
            AiLevel.MEDIUM -> {
                // If behind its bid, try to win; otherwise shed the weakest card.
                if (tricksNeeded > 0 && (myPlayer.tricksWon < (myPlayer.bid ?: 0)) && winningCards.isNotEmpty()) {
                    winningCards.minByOrNull { cardStrength(it, trump) }!!
                } else {
                    losingCards.minByOrNull { cardStrength(it, trump) }
                        ?: winningCards.minByOrNull { cardStrength(it, trump) }!!
                }
            }
            AiLevel.HARD -> {
                if (tricksNeeded > 0 && winningCards.isNotEmpty())
                    winningCards.minByOrNull { cardStrength(it, trump) }!!
                else
                    losingCards.minByOrNull { cardStrength(it, trump) }
                        ?: winningCards.minByOrNull { cardStrength(it, trump) }!!
            }
        }
    }

    private fun cardStrength(c: WizardCard, trump: Suit?): Int = when (c.kind) {
        Kind.JESTER -> 0
        Kind.NUMBER -> (if (c.suit == trump) 100 else 10) + c.value
        Kind.WIZARD -> 1000
    }

    private fun beats(candidate: WizardCard, incumbent: WizardCard?, lead: WizardCard, trump: Suit?): Boolean {
        if (incumbent == null) return candidate.kind == Kind.WIZARD
        return trickWinner(listOf(PlayedCard(0, incumbent), PlayedCard(1, candidate)), trump, 0) == 1
    }

    companion object {
        fun trickWinner(plays: List<PlayedCard>, trump: Suit?, leader: Int): Int {
            require(plays.isNotEmpty())
            val wizards = plays.filter { it.card.kind == Kind.WIZARD }
            if (wizards.isNotEmpty()) return wizards.first().playerIndex
            val firstNonJester = plays.firstOrNull { it.card.kind != Kind.JESTER }?.card
            if (firstNonJester == null) return leader
            val leadSuit = firstNonJester.suit
            fun rank(card: WizardCard): Int = when {
                card.kind == Kind.JESTER -> -1
                card.suit == trump -> 100 + card.value
                card.suit == leadSuit -> 50 + card.value
                else -> 0
            }
            return plays.maxBy { rank(it.card) }.playerIndex
        }
    }
}

object WizardScoring {
    fun points(bid: Int, tricksWon: Int): Int =
        if (bid == tricksWon) 20 + 10 * bid else -10 * kotlin.math.abs(tricksWon - bid)
}
