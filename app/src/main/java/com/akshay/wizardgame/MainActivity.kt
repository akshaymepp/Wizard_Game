package com.akshay.wizardgame

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private val Deep = Color(0xFF070B1A)
private val Navy = Color(0xFF0B1430)
private val Panel = Color(0xE6111830)
private val Gold = Color(0xFFFFD477)
private val Gold2 = Color(0xFFB87925)
private val Purple = Color(0xFF5423A4)
private val Red = Color(0xFFFF5C63)
private val Green = Color(0xFF36D99B)
private val Muted = Color(0xFFB9BED0)

private data class CardFlight(val playerIndex: Int, val card: WizardCard, val handIndex: Int = -1, val sourceX: Dp? = null)
private data class SeatPoint(val x: Float, val y: Float)

class MainActivity : ComponentActivity() {
    fun enterGameFullscreen() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }
    fun exitGameFullscreen() { window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 11, 26)
        window.navigationBarColor = android.graphics.Color.rgb(7, 11, 26)
        setContent { WizardApp() }
    }
}

data class Player(val name: String, val isAI: Boolean = false, val bid: Int? = null, val won: Int = 0, val score: Int = 0)

@Composable
fun WizardApp() {
    val activity = androidx.compose.ui.platform.LocalContext.current as? MainActivity
    var screen by remember { mutableIntStateOf(-1) }
    var players by remember { mutableStateOf(listOf(Player("Akshay"), Player("Rahul"), Player("Nithin"), Player("Arun"), Player("Vishnu"))) }
    var round by remember { mutableIntStateOf(1) }
    var bid by remember { mutableIntStateOf(2) }
    var current by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(20) }
    var room by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("192.168.1.10:8080") }
    var aiCount by remember { mutableIntStateOf(0) }
    var aiDifficulty by remember { mutableStateOf("Medium") }
    var gameMode by remember { mutableStateOf("Offline") }
    var playerCount by remember { mutableIntStateOf(4) }
    var offlineEngine by remember { mutableStateOf<WizardEngine?>(null) }
    var gameRevision by remember { mutableIntStateOf(0) }
    var editingGameSetup by remember { mutableStateOf(false) }
    val cardsEach = round
    val maxRounds = (60 / players.size).coerceAtLeast(1)
    val submitted = players.mapNotNull { it.bid }
    val totalBids = submitted.sum()
    val invalid = submitted.size == players.size && totalBids == cardsEach

    LaunchedEffect(screen, current) {
        if (screen == 1) {
            seconds = 20
            while (seconds > 0 && screen == 1) { delay(1000); seconds-- }
            if (seconds == 0 && players.getOrNull(current)?.bid == null) {
                players = players.toMutableList().also { it[current] = it[current].copy(bid = 0) }
                if (current < players.lastIndex) current++ else screen = 2
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Deep, surface = Panel, primary = Gold)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF111B3D), Deep, Color(0xFF100A22))))) {
            when (screen) {
                -1 -> WelcomeScreen(onOnline = { gameMode = "Private VPN"; screen = 7 }, onOffline = { screen = 4 }, onSettings = { screen = 8 })
                4 -> PlayerSetupScreen(playerCount, { playerCount = it }, aiDifficulty, { aiDifficulty = it }, onBack = {
                    if (editingGameSetup) { editingGameSetup = false; activity?.enterGameFullscreen(); screen = 10 } else screen = -1
                }, onStart = { count ->
                    val names = listOf("Mira", "Rowan", "Sage", "Felix", "Iris", "Jasper", "Nova", "Theo", "Luna")
                    val level = when (aiDifficulty.lowercase()) { "easy" -> WizardEngine.AiLevel.EASY; "hard" -> WizardEngine.AiLevel.HARD; else -> WizardEngine.AiLevel.MEDIUM }
                    offlineEngine = WizardEngine(listOf(players.first().name to false) + (1 until count).map { names[(it - 1) % names.size] to true }, aiLevel = level)
                    aiCount = count - 1; gameMode = "Offline"; gameRevision++; editingGameSetup = false; activity?.enterGameFullscreen(); screen = 10
                })
                10 -> offlineEngine?.let { engine ->
                    OfflineGameScreen(engine, gameRevision, { gameRevision++ }, { activity?.exitGameFullscreen(); screen = -1 }, { editingGameSetup = true; activity?.exitGameFullscreen(); screen = 4 }, { engine.startNewGame(); gameRevision++ })
                } ?: WelcomeScreen(onOnline = { screen = 7 }, onOffline = { screen = 4 }, onSettings = { screen = 8 })
                0 -> MainGame(players, round, maxRounds, cardsEach, onBid = { screen = 1 }, onSettings = { screen = 3 }, onScore = { screen = 2 })
                1 -> BidScreen(players.getOrNull(current)?.name ?: "Player", round, cardsEach, bid,
                    { bid = (bid + 1).coerceAtMost(cardsEach) }, { bid = (bid - 1).coerceAtLeast(0) }, seconds, totalBids, invalid,
                    { value -> players = players.toMutableList().also { it[current] = it[current].copy(bid = value) }; if (current < players.lastIndex) current++ else screen = 2; bid = 0 }, onClose = { screen = 0 })
                2 -> ResultsScreen(players, round, onNext = { players = players.map { it.copy(bid = null, won = 0) }; round = if (round >= maxRounds) 1 else round + 1; current = 0; screen = 0 }, onHome = { screen = 0 })
                3 -> SettingsScreen(players, aiCount, aiDifficulty, gameMode, host, room, { host = it }, { room = it }, { aiDifficulty = it }, { gameMode = it }, { players = it }, { count -> val humans = players.filterNot { it.isAI }; players = humans + (1..count).map { Player("Computer $it", isAI = true) }; aiCount = count }, { screen = 0 })
                7 -> SettingsScreen(players, aiCount, aiDifficulty, gameMode, host, room, { host = it }, { room = it }, { aiDifficulty = it }, { gameMode = it }, { players = it }, { count -> val humans = players.filterNot { it.isAI }; players = humans + (1..count).map { Player("Computer $it", isAI = true) }; aiCount = count }, { screen = -1 }, onlineOnly = true)
                else -> SettingsScreen(players, aiCount, aiDifficulty, gameMode, host, room, { host = it }, { room = it }, { aiDifficulty = it }, { gameMode = it }, { players = it }, { count -> val humans = players.filterNot { it.isAI }; players = humans + (1..count).map { Player("Computer $it", isAI = true) }; aiCount = count }, { screen = -1 }, profileOnly = true)
            }
        }
    }
}

@Composable
private fun WelcomeScreen(onOnline: () -> Unit, onOffline: () -> Unit, onSettings: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF111B3D), Deep, Color(0xFF100A22))))) {
        WelcomeFlyingCards(Modifier.fillMaxSize().zIndex(-1f))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val actions = listOf("PLAY ONLINE" to onOnline, "PLAY OFFLINE" to onOffline, "SETTINGS" to onSettings)
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize().padding(horizontal = maxWidth * .055f, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.fillMaxWidth(.39f), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.Center) {
                        Text("WIZARD", color = Gold, fontSize = 32.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, letterSpacing = 2.sp)
                        Spacer(Modifier.height(10.dp))
                        actions.forEachIndexed { index, item -> Button(onClick = item.second, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = if (index == 1) Purple else Panel), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.5.dp, Gold)) { Text(item.first, color = Gold, fontWeight = FontWeight.Bold) } }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Image(painterResource(R.drawable.welcome_wizard), null, Modifier.fillMaxWidth().fillMaxHeight(.82f), contentScale = ContentScale.Fit)
                        Text("A game of tricks, magic & intuition", color = Muted, fontSize = 14.sp)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Image(painterResource(R.drawable.welcome_wizard), null, Modifier.fillMaxWidth().height(290.dp), contentScale = ContentScale.Fit)
                    Text("WIZARD", color = Gold, fontSize = 40.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif)
                    Text("A game of tricks, magic & intuition", color = Muted, fontSize = 15.sp)
                    Spacer(Modifier.height(20.dp))
                    actions.forEachIndexed { index, item -> Button(onClick = item.second, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = if (index == 1) Purple else Panel), border = androidx.compose.foundation.BorderStroke(1.5.dp, Gold)) { Text(item.first, color = Gold, fontWeight = FontWeight.Bold) } }
                }
            }
        }
    }
}

@Composable
private fun WelcomeFlyingCards(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val width = maxWidth; val height = maxHeight
        val suits = listOf("\u2660", "\u2665", "\u2666", "\u2663", "\u2726", "\u2660")
        val colors = listOf(Color(0xFFB99AFF), Color(0xFFFF7783), Gold, Color(0xFF8DC9FF), Gold, Color(0xFFB99AFF))
        val transition = rememberInfiniteTransition(label = "welcome-card-flight")
        suits.forEachIndexed { index, suit ->
            val progress by transition.animateFloat(-.12f, 1.12f, infiniteRepeatable(tween(6200 + index * 510, delayMillis = index * 430, easing = LinearEasing)), label = "card-flight-$index")
            val startX = index * .21f - .05f; val endX = .95f - index * .14f
            Box(Modifier.offset(x = width * (startX + (endX - startX) * progress), y = height * (.8f - progress * .62f + (index % 3) * .05f)).rotate((index % 2 * 2 - 1) * (18f + progress * 34f)).size(38.dp, 54.dp).background(Color(0xFF32265A).copy(alpha = .38f), RoundedCornerShape(7.dp)).border(1.dp, colors[index].copy(alpha = .38f), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) { Text(suit, color = colors[index].copy(alpha = .48f), fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun PlayerSetupScreen(count: Int, onCount: (Int) -> Unit, difficulty: String, onDifficulty: (String) -> Unit, onBack: () -> Unit, onStart: (Int) -> Unit) {
    ScreenFrame {
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            Text("OFFLINE GAME", color = Gold, fontSize = 23.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Default.ArrowBack, null, tint = Gold) }
        }
        Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            GoldFrame(Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(vertical = 12.dp)) {
                Text("CHOOSE YOUR TABLE", color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp)); Text("How many players are joining?", color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onCount((count - 1).coerceAtLeast(3)) }, modifier = Modifier.size(48.dp).background(Color(0xFF141A31), CircleShape).border(1.dp, Gold2, CircleShape)) { Icon(Icons.Default.Remove, null, tint = Gold) }
                    Box(Modifier.size(94.dp, 80.dp).background(Color(0xFF171329), RoundedCornerShape(20.dp)).border(1.dp, Gold2, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) { Text("$count", color = Gold, fontSize = 46.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif) }
                    IconButton(onClick = { onCount((count + 1).coerceAtMost(6)) }, modifier = Modifier.size(48.dp).background(Color(0xFF141A31), CircleShape).border(1.dp, Gold2, CircleShape)) { Icon(Icons.Default.Add, null, tint = Gold) }
                }
                Spacer(Modifier.height(12.dp)); Text("YOU  +  ${count - 1} AI PLAYERS", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp)); Text("AI DIFFICULTY", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) { listOf("Easy", "Medium", "Hard").forEach { FilterChip(selected = difficulty == it, onClick = { onDifficulty(it) }, label = { Text(it, fontSize = 11.sp) }) } }
            }
        }
        Button(onClick = { onStart(count) }, modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple), border = androidx.compose.foundation.BorderStroke(1.5.dp, Gold)) { Text("DEAL THE CARDS", color = Gold, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun OfflineGameScreen(engine: WizardEngine, revision: Int, onChanged: () -> Unit, onHome: () -> Unit, onEditSetup: () -> Unit, onNewGame: () -> Unit) {
    val state = engine.state
    val playerCount = state.players.size
    val userIndex = state.players.indexOfFirst { !it.isAi }.coerceAtLeast(0)
    val seats = remember(playerCount, userIndex) { playerSeatPoints(state.players, userIndex) }
    var dealStep by remember(engine) { mutableIntStateOf(0) }
    var dealtRound by remember(engine) { mutableIntStateOf(0) }
    var dealFlightStep by remember(engine) { mutableIntStateOf(0) }
    val dealProgress = remember(engine) { Animatable(0f) }
    var flight by remember(engine) { mutableStateOf<CardFlight?>(null) }
    var selectedCard by remember(engine) { mutableStateOf<WizardCard?>(null) }
    var selectedHandIndex by remember(engine) { mutableIntStateOf(-1) }
    val flightProgress = remember(engine) { Animatable(0f) }
    val collectProgress = remember(engine) { Animatable(0f) }
    var menuExpanded by remember { mutableStateOf(false) }
    var activeDialog by remember { mutableStateOf<String?>(null) }
    val cardsToDeal = playerCount * state.round
    val dealing = dealtRound != state.round || dealStep < cardsToDeal
    BackHandler(onBack = { activeDialog = "exit" })

    LaunchedEffect(engine, state.round) {
        dealStep = 0; dealtRound = state.round
        repeat(cardsToDeal) { step ->
            dealFlightStep = step; dealProgress.snapTo(0f); delay(12)
            dealProgress.animateTo(1f, tween(115, easing = FastOutSlowInEasing)); dealStep = step + 1; delay(12)
        }
    }
    LaunchedEffect(flight) {
        val active = flight ?: return@LaunchedEffect
        flightProgress.snapTo(0f); flightProgress.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        if (engine.state.currentPlayerIndex == active.playerIndex && engine.state.trick.size < engine.state.players.size) engine.playCard(active.playerIndex, active.card)
        flight = null; selectedCard = null; selectedHandIndex = -1; onChanged()
    }
    LaunchedEffect(revision, state.round, dealing, flight) {
        if (state.lastSummary != null || dealing || flight != null) return@LaunchedEffect
        if (state.trick.size == playerCount) {
            collectProgress.snapTo(0f); collectProgress.animateTo(1f, tween(720, easing = FastOutSlowInEasing)); delay(120)
            engine.resolveCompletedTrick(); collectProgress.snapTo(0f); onChanged(); return@LaunchedEffect
        }
        if (state.trumpIsWizard && !state.trumpChosen && state.players[state.dealerIndex].isAi) {
            delay(450); val dealerHand = state.players[state.dealerIndex].hand
            engine.chooseTrump(Suit.values().maxByOrNull { suit -> dealerHand.count { it.suit == suit } }?.takeIf { s -> dealerHand.any { it.suit == s } }); onChanged(); return@LaunchedEffect
        }
        if (!state.trumpChosen) return@LaunchedEffect
        if (!state.bidsLocked && state.players[state.bidTurnIndex].isAi) { delay(500); engine.submitRemainingAiBids(); onChanged(); return@LaunchedEffect }
        if (state.bidsLocked && state.players[state.currentPlayerIndex].isAi) { delay(500); flight = CardFlight(state.currentPlayerIndex, engine.chooseAiCardForCurrentPlayer()) }
    }
    if (state.lastSummary != null) {
        OfflineRoundResults(state, state.lastSummary, onNext = { engine.nextRound(); onChanged() }, onNewGame = onNewGame, onHome = onHome)
        return
    }
    val humanTurn = state.bidsLocked && state.currentPlayerIndex == userIndex && state.trick.size < playerCount
    val legalCards = if (humanTurn) engine.legalCards(userIndex).toSet() else emptySet()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth <= maxHeight) {
            Image(painterResource(R.drawable.cozy_living_room_game_table), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color(0xC9000000)), contentAlignment = Alignment.Center) { Text("Please rotate your device to landscape mode.", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp)) }
            return@BoxWithConstraints
        }
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val widthLimited = (availableWidth - 28.dp) / (state.round * .42f).coerceAtLeast(3f) / .64f
        val cardHeight = minOf(availableHeight * .21f, widthLimited).coerceIn(48.dp, 126.dp)
        val cardWidth = cardHeight * .64f
        val hand = state.players[userIndex].hand
        val bidding = !state.bidsLocked
        val choosingTrump = state.trumpIsWizard && !state.trumpChosen && state.dealerIndex == userIndex
        Image(painterResource(R.drawable.cozy_living_room_game_table), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color(0x33050A10)))
        Row(Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            StatusPanel("ROUND", "${state.round} / ${state.maxRounds}")
            StatusPanel("CARDS  ·  BIDS", "${state.cardsPerPlayer}     ${state.bidTotal} / ${state.round * playerCount}")
        }
        Row(Modifier.align(Alignment.TopEnd).padding(end = 9.dp, top = 7.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            StatusPanel("TRUMP", state.trump?.let(::suitMark) ?: if (state.trumpIsWizard && !state.trumpChosen) "CHOOSE" else "NONE", state.trump?.let(::suitColor) ?: Gold)
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xDD160F0E)).border(1.dp, Gold2, CircleShape)) { Icon(Icons.Default.Menu, "Game menu", tint = Gold) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Resume Game") }, onClick = { menuExpanded = false })
                    DropdownMenuItem(text = { Text("Game Rules") }, onClick = { menuExpanded = false; activeDialog = "rules" })
                    DropdownMenuItem(text = { Text("Settings") }, onClick = { menuExpanded = false; activeDialog = "settings" })
                    DropdownMenuItem(text = { Text("Restart Game") }, onClick = { menuExpanded = false; activeDialog = "restart" })
                    DropdownMenuItem(text = { Text("Exit Game") }, onClick = { menuExpanded = false; activeDialog = "exit" })
                }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            state.trick.forEachIndexed { trickIndex, played ->
                val seat = seats.getValue(played.playerIndex); val collect = collectProgress.value
                val ox = availableWidth * (seat.x - .5f) * .46f * (1f + collect * 1.5f)
                val oy = availableHeight * (seat.y - .5f) * .29f * (1f + collect * 1.5f)
                val c = played.card
                Box(Modifier.offset(x = ox, y = oy).zIndex(trickIndex.toFloat() + 1f)) { PlayingCard(c.label, c.suit?.let(::suitMark) ?: if (c.kind == Kind.WIZARD) "W" else "J", c.suit?.let(::suitColor) ?: Gold, c.kind != Kind.NUMBER, Modifier.graphicsLayer(alpha = 1f - collect), cardWidth = cardWidth * .68f, cardHeight = cardHeight * .68f) }
            }
        }
        state.players.forEachIndexed { index, player ->
            if (index == userIndex) return@forEachIndexed
            val point = seats.getValue(index); val seatWidth = (availableWidth * .205f).coerceIn(112.dp, 178.dp)
            GameSeat(player, dealer = state.dealerIndex == index, active = if (bidding) state.bidTurnIndex == index else state.currentPlayerIndex == index, cardCount = player.hand.size,
                modifier = Modifier.offset(x = availableWidth * point.x - seatWidth / 2f, y = availableHeight * point.y).width(seatWidth))
        }
        if (dealing) {
            val point = seats[dealFlightStep % playerCount] ?: SeatPoint(.5f, .88f); val p = dealProgress.value
            Box(Modifier.offset(x = availableWidth * (.5f + (point.x - .5f) * p) - 13.dp, y = availableHeight * (.49f + (point.y - .49f) * p) - 19.dp).zIndex(15f)
                .size(26.dp, 38.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF8F1D2B)).border(1.dp, Color.White, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) { Text("✦", color = Gold, fontSize = 13.sp) }
        }
        val user = state.players[userIndex]
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.52f).padding(horizontal = 9.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
            Row(Modifier.clip(RoundedCornerShape(15.dp)).background(Color(0xD91A1110)).border(1.dp, if (humanTurn || bidding && state.bidTurnIndex == userIndex) Gold else Gold2, RoundedCornerShape(15.dp)).padding(horizontal = 11.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.dealerIndex == userIndex) Text("♛  ", color = Gold, fontSize = 13.sp)
                Text("${user.name}  |  ${user.hand.size} CARDS  |  BID ${user.bid ?: "—"}  |  WON ${user.tricksWon}", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (choosingTrump) {
                Text("CHOOSE TRUMP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Suit.values().forEach { s -> Button(onClick = { engine.chooseTrump(s); onChanged() }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), modifier = Modifier.height(34.dp), colors = ButtonDefaults.buttonColors(containerColor = suitColor(s))) { Text(suitName(s), fontSize = 10.sp) } }
                    TextButton(onClick = { engine.chooseTrump(null); onChanged() }) { Text("NO TRUMP", color = Gold, fontSize = 10.sp) }
                }
            } else if (bidding && state.bidTurnIndex == userIndex) {
                LazyRow(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) { items(engine.legalBidsFor(userIndex)) { value -> Button(onClick = { engine.submitBid(userIndex, value); selectedCard = null; selectedHandIndex = -1; onChanged() }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("BID $value", color = Gold, fontSize = 11.sp) } } }
            } else if (humanTurn) Text(if (selectedCard == null) "SELECT A CARD TO PLAY" else "${selectedCard?.label} SELECTED", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            // Keep the full face of each card visible and distribute the hand evenly
            // across the lower table instead of stacking the cards tightly in the center.
            val step = if (hand.size > 1) ((availableWidth - cardWidth - 36.dp) / (hand.size - 1)).coerceAtMost(cardWidth + 8.dp) else cardWidth
            val cardGap = (step - cardWidth).coerceAtLeast(0.dp)
            Row(Modifier.fillMaxWidth().height(cardHeight + 10.dp), horizontalArrangement = Arrangement.spacedBy(cardGap, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
                hand.forEachIndexed { i, card ->
                    val canSelect = !bidding && humanTurn && card in legalCards && flight == null; val chosen = selectedHandIndex == i
                    Box(Modifier.width(cardWidth).height(cardHeight).offset(y = if (chosen) (-8).dp else 0.dp).clickable(enabled = canSelect) {
                        if (chosen) { selectedCard = null; selectedHandIndex = -1 } else { selectedCard = card; selectedHandIndex = i }
                    }) {
                        val cardModifier = Modifier.graphicsLayer(alpha = if (canSelect || !humanTurn || bidding) 1f else .62f)
                        if (card.kind == Kind.WIZARD) Image(painterResource(R.drawable.wizard_card), "Wizard card", cardModifier.width(cardWidth).height(cardHeight).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
                        else PlayingCard(card.label, card.suit?.let(::suitMark) ?: "J", card.suit?.let(::suitColor) ?: Gold, card.kind != Kind.NUMBER, cardModifier, cardWidth, cardHeight)
                        if (chosen) Box(Modifier.fillMaxSize().border(2.dp, Gold, RoundedCornerShape(9.dp)))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { activeDialog = "settings" }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Gold2)) { Text("EDIT", fontSize = 11.sp) }
                Button(onClick = { selectedCard?.let { flight = CardFlight(userIndex, it) } }, enabled = humanTurn && selectedCard != null && selectedCard in legalCards && flight == null, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 7.dp), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF237A4B), disabledContainerColor = Color(0xFF343C36)), border = androidx.compose.foundation.BorderStroke(1.5.dp, if (humanTurn && selectedCard in legalCards) Color(0xFF83E0A8) else Gold2)) { Text("PLAY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }
        flight?.let { active ->
            val start = seats[active.playerIndex] ?: SeatPoint(.5f, .88f); val p = flightProgress.value
            val x = start.x + (.5f - start.x) * p; val y = start.y + (.49f - start.y) * p
            Box(Modifier.offset(x = availableWidth * x - cardWidth / 2f, y = availableHeight * y - cardHeight / 2f).zIndex(25f)) {
                val c = active.card
                PlayingCard(c.label, c.suit?.let(::suitMark) ?: if (c.kind == Kind.WIZARD) "W" else "J", c.suit?.let(::suitColor) ?: Gold, c.kind != Kind.NUMBER, cardWidth = cardWidth * .72f, cardHeight = cardHeight * .72f)
            }
        }
        if (activeDialog != null) {
            val dialog = activeDialog
            AlertDialog(onDismissRequest = { activeDialog = null }, title = { Text(when (dialog) { "rules" -> "Wizard Rules"; "settings" -> "Game Settings"; "restart" -> "Restart Game?"; else -> "Exit Game?" }) },
                text = { Text(when (dialog) { "rules" -> "Bid your expected tricks. Follow suit when able. Wizards are highest, Jesters are lowest, and trump beats other suits."; "settings" -> "Open setup to edit players or AI difficulty. The current game will stay paused if you return without dealing a new game."; "restart" -> "Start a fresh game with the same players? Current round progress will be lost."; else -> "Leave this game and return home? Current round progress will be lost." }) },
                confirmButton = { TextButton(onClick = { when (dialog) { "restart" -> { selectedCard = null; selectedHandIndex = -1; onNewGame() }; "exit" -> onHome(); "settings" -> onEditSetup() }; activeDialog = null }) { Text(if (dialog == "restart") "RESTART" else if (dialog == "exit") "EXIT" else if (dialog == "settings") "EDIT SETUP" else "OK") } },
                dismissButton = { if (dialog == "restart" || dialog == "exit") TextButton(onClick = { activeDialog = null }) { Text("CANCEL") } })
        }
    }
}
@Composable
private fun GameSeat(player: WizardPlayer, dealer: Boolean, active: Boolean, cardCount: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (dealer) Text("♛", color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth().shadow(5.dp, RoundedCornerShape(13.dp)).clip(RoundedCornerShape(13.dp)).background(Color(0xD91A1110))
            .border(if (active) 1.7.dp else 1.dp, if (active) Color(0xFFFFD477) else Color(0xFF9F7951).copy(alpha = .85f), RoundedCornerShape(13.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(player.name, color = if (active) Color(0xFFFFD477) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text("${player.hand.size} cards  ·  Bid ${player.bid ?: "—"}  ·  Won ${player.tricksWon}", color = Color(0xFFE5D6C3), fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        OpponentCardFan(cardCount)
    }
}

@Composable
private fun OpponentCardFan(cardCount: Int) {
    val visible = cardCount.coerceAtMost(6)
    if (visible > 0) Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy((-11).dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(visible) { index ->
            val angle = (index - (visible - 1) / 2f) * 6f
            Box(Modifier.width(26.dp).height(37.dp).rotate(angle).shadow(3.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF8F1D2B)).border(1.dp, Color.White, RoundedCornerShape(4.dp)).padding(2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().border(.7.dp, Color(0xFFEAD9C9), RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                    Text("✦", color = Color(0xFFF1D8C2), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusPanel(label: String, value: String, valueColor: Color = Color.White) {
    Column(Modifier.clip(RoundedCornerShape(11.dp)).background(Color(0xD9120E0D))
        .border(1.dp, Gold2.copy(alpha = .8f), RoundedCornerShape(11.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(label, color = Color(0xFFD3C5B3), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
@Composable
private fun OfflineRoundResults(state: WizardGameState, summary: RoundSummary, onNext: () -> Unit, onNewGame: () -> Unit, onHome: () -> Unit) {
    ScreenFrame {
        Text(if (state.finished) "THE GAME IS COMPLETE" else "ROUND ${summary.round} COMPLETE", color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, letterSpacing = 1.4.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        if (state.finished) {
            val winner = state.players.maxByOrNull { it.totalScore }
            Text("${winner?.name} WINS", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                GoldFrame(Modifier.fillMaxWidth()) {
                    Text("ROUND ${summary.round} RESULTS", color = Gold, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                    summary.rows.sortedByDescending { it.totalScore }.forEach { player ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(player.name, color = Color.White, modifier = Modifier.weight(1.2f), maxLines = 1)
                            Text("Bid ${player.bid}", color = Muted, modifier = Modifier.weight(.7f), textAlign = TextAlign.Center)
                            Text("Won ${player.tricksWon}", color = Muted, modifier = Modifier.weight(.8f), textAlign = TextAlign.Center)
                            Text("${if (player.roundPoints >= 0) "+" else ""}${player.roundPoints}", color = if (player.roundPoints >= 0) Green else Red, modifier = Modifier.weight(.7f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                            Text("Total ${player.totalScore}", color = Gold, modifier = Modifier.weight(.9f), textAlign = TextAlign.End, fontSize = 11.sp)
                        }
                        HorizontalDivider(color = Gold2.copy(alpha = .45f))
                    }
                }
            }
            if (state.roundHistory.size > 1) item {
                GoldFrame(Modifier.fillMaxWidth()) {
                    Text("PREVIOUS ROUNDS", color = Gold, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                    state.roundHistory.dropLast(1).reversed().forEach { old ->
                        Text("Round ${old.round}: ${old.rows.sortedByDescending { it.totalScore }.first().name} led • ${old.rows.sumOf { it.roundPoints }} points scored", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
        }
        if (state.finished) {
            Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple), border = androidx.compose.foundation.BorderStroke(1.dp, Gold)) { Text("START A NEW GAME", color = Gold, fontWeight = FontWeight.Bold) }
        } else {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple), border = androidx.compose.foundation.BorderStroke(1.dp, Gold)) { Text("NEXT ROUND", color = Gold, fontWeight = FontWeight.Bold) }
        }
        Text("BACK TO HOME", Modifier.clickable(onClick = onHome).padding(10.dp), color = Muted, fontSize = 12.sp)
    }
}

private fun playerSeatPoints(players: List<WizardPlayer>, userIndex: Int): Map<Int, SeatPoint> {
    val opponents = players.indices.filter { it != userIndex }
    val opponentPoints = when (players.size) {
        3 -> listOf(SeatPoint(.27f, .20f), SeatPoint(.73f, .20f))
        4 -> listOf(SeatPoint(.12f, .35f), SeatPoint(.50f, .17f), SeatPoint(.88f, .35f))
        5 -> listOf(SeatPoint(.15f, .20f), SeatPoint(.85f, .20f), SeatPoint(.85f, .40f), SeatPoint(.15f, .40f))
        else -> listOf(
            SeatPoint(.13f, .20f), SeatPoint(.50f, .15f), SeatPoint(.87f, .20f),
            SeatPoint(.83f, .40f), SeatPoint(.17f, .40f)
        )
    }
    return buildMap {
        put(userIndex, SeatPoint(.50f, .88f))
        opponents.forEachIndexed { seat, playerIndex -> put(playerIndex, opponentPoints[seat]) }
    }
}

private fun suitName(suit: Suit): String = when (suit) {
    Suit.RED -> "RED"
    Suit.BLUE -> "BLUE"
    Suit.GREEN -> "GREEN"
    Suit.YELLOW -> "YELLOW"
}

private fun suitColor(suit: Suit): Color = when (suit) {
    Suit.RED -> Color(0xFFB52236)
    Suit.BLUE -> Color(0xFF254E9B)
    Suit.GREEN -> Color(0xFF18734A)
    Suit.YELLOW -> Color(0xFFB57517)
}

private fun suitMark(suit: Suit): String = when (suit) {
    Suit.RED -> "\u2665"
    Suit.BLUE -> "\u2660"
    Suit.GREEN -> "\u2663"
    Suit.YELLOW -> "\u2666"
}
@Composable
private fun ScreenFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101A3B), Deep, Color(0xFF120B25))))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
private fun GoldFrame(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF9161B37), Color(0xF9081027), Color(0xF91B102D))))
            .border(1.2.dp, Brush.verticalGradient(listOf(Gold, Gold2, Gold)), RoundedCornerShape(24.dp))
            .padding(12.dp),
        content = content
    )
}

@Composable
private fun FantasyTitle(title: String, subtitle: String) {
    Text("♛  W I Z A R D", color = Gold, fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text(title, color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    Text(subtitle, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
}

@Composable
private fun RoundPill(text: String) {
    Box(Modifier.padding(vertical = 5.dp).border(1.dp, Gold2, RoundedCornerShape(30.dp)).background(Color(0xFF10172D), RoundedCornerShape(30.dp)).padding(horizontal = 22.dp, vertical = 8.dp)) {
        Text(text, color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = .4.sp)
    }
}

@Composable
private fun PlayerBadge(player: Player, index: Int, modifier: Modifier = Modifier) {
    val avatarColors = listOf(Color(0xFFB87925), Color(0xFF357A69), Color(0xFF8D503A), Color(0xFF385D91), Color(0xFF7150A2))
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xE9081024)).border(1.dp, Gold2.copy(alpha=.85f), RoundedCornerShape(16.dp)).padding(horizontal = 9.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PlayerPortrait(index, 48.dp)
        Text(player.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text("Bid: ${player.bid?.toString() ?: "—"}   Won: ${player.won}", color = Gold, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun PlayerPortrait(index: Int, size: androidx.compose.ui.unit.Dp = 42.dp) {
    val portrait = when (index % 5) {
        1 -> R.drawable.portrait_rahul
        2 -> R.drawable.portrait_nithin
        3 -> R.drawable.portrait_arun
        4 -> R.drawable.portrait_vishnu
        else -> R.drawable.portrait_rahul
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(0xFF080D1C))
            .border(2.dp, Gold, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(portrait),
            contentDescription = "Player portrait",
            modifier = Modifier.fillMaxSize().clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun PlayingCard(
    label: String,
    suit: String,
    color: Color,
    special: Boolean = false,
    modifier: Modifier = Modifier,
    cardWidth: androidx.compose.ui.unit.Dp = 68.dp,
    cardHeight: androidx.compose.ui.unit.Dp = 106.dp
) {
    if (special && (label == "W" || label == "J")) {
        Image(
            painter = painterResource(if (label == "W") R.drawable.wizard_card else R.drawable.jester_card),
            contentDescription = if (label == "W") "Wizard card" else "Jester card",
            modifier = modifier.width(cardWidth).height(cardHeight).clip(RoundedCornerShape(9.dp)),
            contentScale = ContentScale.Fit
        )
    } else {
        StandardPlayingCard(label, suit, color, modifier, cardWidth, cardHeight)
    }
}

@Composable
private fun StandardPlayingCard(label: String, suit: String, baseColor: Color, modifier: Modifier, cardWidth: Dp, cardHeight: Dp) {
    val prefix = label.firstOrNull()
    val value = label.drop(1).toIntOrNull() ?: label.toIntOrNull()
    val rank = when (value) {
        11 -> "J"
        12 -> "Q"
        13 -> "K"
        14 -> "A"
        else -> value?.toString() ?: label
    }
    val mark = when (prefix) {
        'R' -> "\u2665"
        'B' -> "\u2660"
        'G' -> "\u2663"
        'Y' -> "\u2666"
        else -> suit
    }
    val ink = when (prefix) {
        'R', 'Y' -> Color(0xFFB52236)
        'B', 'G' -> Color(0xFF171717)
        else -> baseColor
    }
    val cornerSize = (cardWidth.value * .18f).coerceIn(8f, 18f).sp
    Box(
        modifier.width(cardWidth).height(cardHeight).clip(RoundedCornerShape(9.dp))
            .background(Color(0xFFFFF9EE), RoundedCornerShape(9.dp))
            .border(2.dp, Color(0xFF090909), RoundedCornerShape(9.dp))
            .padding(4.dp)
    ) {
        Box(Modifier.fillMaxSize().border(.7.dp, Color(0xFFC4AF8B), RoundedCornerShape(6.dp)))
        Column(Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(rank, color = ink, fontSize = cornerSize, fontWeight = FontWeight.Black, lineHeight = cornerSize)
            Text(mark, color = ink, fontSize = cornerSize * .82f, lineHeight = cornerSize)
        }
        Column(Modifier.align(Alignment.BottomEnd).rotate(180f).padding(start = 3.dp, top = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(rank, color = ink, fontSize = cornerSize, fontWeight = FontWeight.Black, lineHeight = cornerSize)
            Text(mark, color = ink, fontSize = cornerSize * .82f, lineHeight = cornerSize)
        }
        if (value != null && value in 2..10) {
            val rows = (value + 1) / 2
            Column(
                Modifier.fillMaxSize().padding(horizontal = cardWidth * .17f, vertical = cardHeight * .12f),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(rows) { row ->
                    val singlePipRow = value % 2 == 1 && row == rows / 2
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (singlePipRow) Arrangement.Center else Arrangement.SpaceBetween) {
                        repeat(if (singlePipRow) 1 else 2) {
                            Text(mark, color = ink, fontSize = (cardWidth.value * .25f).coerceIn(12f, 28f).sp)
                        }
                    }
                }
            }
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                if (rank in setOf("J", "Q", "K", "A")) {
                    Text(rank, color = ink, fontSize = (cardWidth.value * .42f).coerceIn(22f, 48f).sp, fontWeight = FontWeight.Bold)
                }
                Text(mark, color = ink, fontSize = (cardWidth.value * .48f).coerceIn(26f, 54f).sp)
            }
        }
    }
}

private data class TableCard(val label: String, val suit: String, val color: Color, val special: Boolean = false)

@Composable
private fun PlayedCardsStack() {
    var expanded by remember { mutableStateOf(false) }
    val playedCards = listOf(
        TableCard("W", "♛", Gold, true),
        TableCard("7", "⚔", Color(0xFF16895E)),
        TableCard("11", "⚔", Color(0xFFC28B20)),
        TableCard("3", "⚔", Color(0xFFB52B2B))
    )

    Box(
        Modifier
            .width(280.dp)
            .height(142.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x33100B25))
            .border(1.dp, Gold2.copy(alpha = .55f), RoundedCornerShape(18.dp))
            .clickable { expanded = true },
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            playedCards.forEachIndexed { index, card ->
                Box(Modifier.offset(x = (-index * 13).dp)) {
                    PlayingCard(
                        card.label, card.suit, card.color, card.special,
                        cardWidth = 67.dp, cardHeight = 104.dp
                    )
                }
            }
        }
        Text(
            "Tap to view all cards",
            color = Gold,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)
        )
    }

    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF171332), Color(0xFF080D20))))
                    .border(1.5.dp, Gold, RoundedCornerShape(22.dp))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("CARDS PLAYED", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                    Text("✕", color = Gold, fontSize = 22.sp, modifier = Modifier.clickable { expanded = false }.padding(4.dp))
                }
                Text("Tap a card area to inspect the played cards", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(14.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(playedCards) { card ->
                        PlayingCard(
                            card.label, card.suit, card.color, card.special,
                            cardWidth = 104.dp, cardHeight = 158.dp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Swipe horizontally to see the full queue", color = Gold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MainGame(players: List<Player>, round: Int, maxRounds: Int, cards: Int, onBid: () -> Unit, onSettings: () -> Unit, onScore: () -> Unit) {
    ScreenFrame {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, null, tint = Gold) }
            Text("♛ WIZARD ♛", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif)
            IconButton(onClick = onScore) { Icon(Icons.Default.BarChart, null, tint = Gold) }
        }
        RoundPill("Round $round / $maxRounds")
        Text("‹   Cards per player: $cards   ›", color = Color.White, fontFamily = FontFamily.Serif, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        GoldFrame(Modifier.fillMaxWidth().weight(1f)) {
            Text("THE ENCHANTED TABLE", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth(.96f).fillMaxHeight(.86f).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF293052), Color(0xFF121A35), Color(0xFF070C1B))))
                    .border(5.dp, Brush.sweepGradient(listOf(Gold2, Gold, Color(0xFF39264C), Gold2)), CircleShape))
                val topLeft = players.getOrNull(1)
                val topRight = players.getOrNull(2)
                val left = players.getOrNull(3)
                val right = players.getOrNull(4)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        topLeft?.let { PlayerBadge(it, 1) }
                        topRight?.let { PlayerBadge(it, 2) }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        left?.let { PlayerBadge(it, 3) }
                        PlayedCardsStack()
                        right?.let { PlayerBadge(it, 4) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        PlayerBadge(players.first(), 0)
                    }
                }
            }
            Text("YOUR HAND  •  $cards CARDS", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                val labels = listOf("J", "4", "8", "10", "W", "3", "Q")
                val suits = listOf("♟", "♣", "♣", "♠", "♛", "♦", "♥")
                val colors = listOf(Color.Red, Color(0xFFB87925), Color(0xFF187B50), Color(0xFF164C9B), Gold, Color.Red, Color.Red)
                repeat(cards.coerceAtMost(5)) { i ->
                    Box(Modifier.offset(x = (-i * 12).dp)) {
                        PlayingCard(labels[i], suits[i], colors[i], i == 4, cardWidth = 76.dp, cardHeight = 118.dp)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallNav("ⓘ", "Game Info", Modifier.weight(1f)) { onSettings() }
            Button(onClick = onBid, modifier = Modifier.weight(1.8f).height(58.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(2.dp, Gold)) {
                Text("⚒  Place Bid", color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            }
            SmallNav("▥", "Score", Modifier.weight(1f)) { onScore() }
        }
    }
}

@Composable
private fun SmallNav(icon: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.height(54.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF10172D)).border(1.dp, Gold2, RoundedCornerShape(13.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(icon, color = Gold, fontSize = 17.sp)
        Text(label, color = Color.White, fontSize = 9.sp)
    }
}

@Composable
private fun BidScreen(name: String, round: Int, cards: Int, bid: Int, plus: () -> Unit, minus: () -> Unit, seconds: Int, total: Int, invalid: Boolean, confirm: (Int) -> Unit, onClose: () -> Unit) {
    ScreenFrame {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = Gold) }
        }
        Spacer(Modifier.height(5.dp))
        Text("🧙‍♂️          🃏", color = Gold, fontSize = 54.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        GoldFrame(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Box(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.background(Brush.horizontalGradient(listOf(Gold2, Gold, Gold2)), RoundedCornerShape(28.dp)).padding(horizontal = 30.dp, vertical = 7.dp)) {
                    Text("PLACE YOUR BID", color = Color(0xFF20132A), fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif)
                }
            }
            Text("Round $round  •  $cards cards per player", color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(15.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
              Box(Modifier.size(116.dp).border(5.dp, Brush.sweepGradient(listOf(Gold, Color(0xFF9D55DD), Gold)), CircleShape).padding(8.dp).border(2.dp, Gold2, CircleShape), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⌛", color = Gold, fontSize = 26.sp)
                    Text("${seconds}s", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Black)
                }
              }
            }
            Spacer(Modifier.height(12.dp))
            Text("YOUR TURN", color = Gold, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            Text(name, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                RoundControl("−", minus)
                Box(Modifier.padding(horizontal = 12.dp).width(150.dp).height(82.dp).clip(RoundedCornerShape(22.dp)).background(Brush.verticalGradient(listOf(Color(0xFF5A3A14), Color(0xFF1B1420)))).border(3.dp, Gold, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                    Text("$bid", color = Gold, fontSize = 54.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif)
                }
                RoundControl("+", plus)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..cards).forEach { v ->
                    Box(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(10.dp)).background(if (v == bid) Brush.verticalGradient(listOf(Color(0xFF8A5A16), Color(0xFF3B2513))) else Brush.verticalGradient(listOf(Color(0xFF171D31), Color(0xFF0A1022))).let { it }).border(1.dp, if (v == bid) Gold else Gold2.copy(alpha=.5f), RoundedCornerShape(10.dp)).clickable { confirm(v) }, contentAlignment = Alignment.Center) {
                        Text("$v", color = if (v == bid) Gold else Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0C1226)).border(1.dp, if (invalid) Red else Gold2, RoundedCornerShape(14.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Bids", color = Color.White, fontSize = 11.sp)
                    Text("$total", color = Gold, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.width(1.dp).height(42.dp).background(Gold2))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Round Cards", color = Color.White, fontSize = 11.sp)
                    Text("$cards", color = Gold, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                }
                if (invalid) {
                    Column(Modifier.weight(1.7f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⊗", color = Red, fontSize = 22.sp)
                        Text("Total bids cannot equal round cards ($cards)", color = Red, fontSize = 9.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(17.dp))
            Button(onClick = { confirm(bid) }, enabled = seconds > 0 && !invalid, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple, disabledContainerColor = Color(0xFF333747)), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.5.dp, Gold)) {
                Text("✓  CONFIRM BID", color = if (seconds > 0 && !invalid) Gold else Muted, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            }
        }
    }
}

@Composable
private fun RoundControl(symbol: String, action: () -> Unit) {
    Box(Modifier.size(62.dp).clip(CircleShape).background(Color(0xFF11182F)).border(2.dp, Gold, CircleShape).clickable(onClick = action), contentAlignment = Alignment.Center) {
        Text(symbol, color = Gold, fontSize = 38.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun ResultsScreen(players: List<Player>, round: Int, onNext: () -> Unit, onHome: () -> Unit) {
    ScreenFrame {
        Spacer(Modifier.height(12.dp))
        Text("♛", color = Gold, fontSize = 48.sp)
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF160D2C), Color(0xFF65339B), Color(0xFF160D2C))), RoundedCornerShape(28.dp)).border(2.dp, Brush.horizontalGradient(listOf(Gold2, Gold, Gold2)), RoundedCornerShape(28.dp)).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text("ROUND $round COMPLETE", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif)
        }
        Text("Round Summary", color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
        GoldFrame(Modifier.fillMaxWidth().weight(1f)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Player", color = Gold, modifier = Modifier.weight(1.5f), fontSize = 12.sp)
                Text("Bid", color = Gold, modifier = Modifier.weight(.5f), textAlign = TextAlign.Center, fontSize = 12.sp)
                Text("Won", color = Gold, modifier = Modifier.weight(.5f), textAlign = TextAlign.Center, fontSize = 12.sp)
                Text("Points", color = Gold, modifier = Modifier.weight(.7f), textAlign = TextAlign.Center, fontSize = 12.sp)
            }
            HorizontalDivider(color = Gold2)
            players.forEach { p ->
                val pts = if (p.bid == p.won) 20 + 10 * (p.bid ?: 0) else -10 * kotlin.math.abs((p.bid ?: 0) - p.won)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1.5f), verticalAlignment = Alignment.CenterVertically) {
                        PlayerPortrait(players.indexOf(p), 30.dp)
                        Spacer(Modifier.width(6.dp))
                        Text((if (p.name == "Akshay") "Akshay (You)" else p.name), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    Text("${p.bid ?: 0}", color = Color.White, modifier = Modifier.weight(.5f), textAlign = TextAlign.Center)
                    Text("${p.won}", color = Color.White, modifier = Modifier.weight(.5f), textAlign = TextAlign.Center)
                    Text(if (pts >= 0) "+$pts" else "$pts", color = if (pts >= 0) Green else Red, modifier = Modifier.weight(.7f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = Color(0xFF34334A))
            }
            Spacer(Modifier.height(14.dp))
            Text("TOTAL SCORE  (Till Round $round)", color = Gold, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                players.take(5).forEachIndexed { i, p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        PlayerPortrait(i, 42.dp)
                        Text(p.name.take(7), color = Color.White, fontSize = 9.sp, maxLines = 1)
                        Text("${p.score}", color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Button(onClick = onNext, modifier = Modifier.weight(1f).height(55.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple), border = androidx.compose.foundation.BorderStroke(1.5.dp, Gold), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.PlayArrow, null, tint = Gold); Text("Next Round", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            OutlinedButton(onClick = onHome, modifier = Modifier.weight(1f).height(55.dp), border = androidx.compose.foundation.BorderStroke(1.5.dp, Gold), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.BarChart, null, tint = Gold); Text("Full Scoreboard", color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    players: List<Player>, aiCount: Int, aiDifficulty: String, gameMode: String, host: String, room: String,
    setHost: (String) -> Unit, setRoom: (String) -> Unit, setAiDifficulty: (String) -> Unit, setGameMode: (String) -> Unit,
    update: (List<Player>) -> Unit, setAiCount: (Int) -> Unit, back: () -> Unit,
    onlineOnly: Boolean = false, profileOnly: Boolean = false
) {
    val humans = players.filterNot { it.isAI }
    val maxAi = (10 - humans.size).coerceAtLeast(0)
    var onlineActionMessage by remember { mutableStateOf<String?>(null) }
    if (profileOnly) {
        val playerName = players.firstOrNull()?.name.orEmpty()
        ScreenFrame {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                    Text("GAME SETTINGS", color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, letterSpacing = 1.5.sp)
                    IconButton(onClick = back, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Default.ArrowBack, null, tint = Gold) }
                }
                GoldFrame(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
                    Text("YOUR PLAYER NAME", color = Gold, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, letterSpacing = 1.sp)
                    OutlinedTextField(
                        value = playerName,
                        onValueChange = { name ->
                            if (players.isNotEmpty()) update(players.toMutableList().also { it[0] = it[0].copy(name = name) })
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Player name") }
                    )
                }
                Spacer(Modifier.height(22.dp))
                Button(onClick = back, modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple), border = androidx.compose.foundation.BorderStroke(1.5.dp, Gold), shape = RoundedCornerShape(17.dp)) {
                    Text("SAVE & RETURN", color = Gold, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                }
            }
        }
        return
    }
    ScreenFrame {
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            Text(if (onlineOnly) "PLAY ONLINE" else "GAME SETTINGS", color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, letterSpacing = 1.5.sp)
            IconButton(onClick = back, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Default.ArrowBack, null, tint = Gold) }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                GoldFrame(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
                    if (!onlineOnly) {
                        Text("GAME MODE", color = Gold, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Offline", "Private VPN").forEach { mode ->
                                FilterChip(selected = gameMode == mode, onClick = { setGameMode(mode) }, label = { Text(mode) })
                            }
                        }
                    }
                    Text("AI PLAYERS", color = Gold, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, letterSpacing = 1.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { setAiCount((aiCount - 1).coerceAtLeast(0)) }, enabled = aiCount > 0) { Icon(Icons.Default.Remove, null, tint = Gold) }
                        Text("$aiCount AI  •  ${humans.size + aiCount} TOTAL", color = Gold, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { setAiCount((aiCount + 1).coerceAtMost(maxAi)) }, enabled = aiCount < maxAi) { Icon(Icons.Default.Add, null, tint = Gold) }
                    }
                    Text("AI DIFFICULTY", color = Gold, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, letterSpacing = 1.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("Easy", "Medium", "Hard").forEach { level -> FilterChip(selected = aiDifficulty == level, onClick = { setAiDifficulty(level) }, label = { Text(level) }) }
                    }
                }
            }
            if (!onlineOnly) item {
                GoldFrame(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
                    Text("PLAYER NAMES", color = Gold, fontWeight = FontWeight.Bold)
                    humans.forEachIndexed { i, p ->
                        OutlinedTextField(value = p.name, onValueChange = { v ->
                            val idx = players.indexOf(p)
                            if (idx >= 0) update(players.toMutableList().also { it[idx] = p.copy(name = v) })
                        }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Player ${i + 1}") })
                    }
                }
            }
            if (onlineOnly || gameMode == "Private VPN") item {
                GoldFrame(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
                    Text(if (onlineOnly) "HOST OR JOIN A GAME" else "PRIVATE VPN SERVER", color = Gold, fontWeight = FontWeight.Bold)
                    OutlinedTextField(host, setHost, modifier = Modifier.fillMaxWidth(), label = { Text("VPN server IP:port") })
                    OutlinedTextField(room, setRoom, modifier = Modifier.fillMaxWidth(), label = { Text("Room code") })
                    if (onlineOnly) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onlineActionMessage = "Hosting is unavailable until online networking is connected." },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Purple),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Gold)
                            ) { Text("HOST", color = Gold, fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = { onlineActionMessage = "Joining is unavailable until online networking is connected." },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Panel),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Gold)
                            ) { Text("JOIN", color = Gold, fontWeight = FontWeight.Bold) }
                        }
                        onlineActionMessage?.let { Text(it, color = Muted, fontSize = 12.sp) }
                    }
                }
            }
        }
        Button(onClick = back, modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple), border = androidx.compose.foundation.BorderStroke(1.5.dp, Gold), shape = RoundedCornerShape(17.dp)) {
            Text("SAVE & RETURN", color = Gold, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        }
    }
}
