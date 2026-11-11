package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.design.StructColors

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

internal enum class IntroductionStep(val eyebrow: String, val title: String, val body: String) {
    OVERVIEW("A LITTLE STRUCTURE", "Your portfolio.\nA clearer picture.",
        "Bring your Rwanda bond and share records together, so the details make more sense."),
    IMPORT("FROM PAPER TO PERSPECTIVE", "Less typing.\nMore understanding.",
        "Turn a broker PDF into clear bond and share records. Review the details before they reach your portfolio."),
    CALENDAR("MAKE ROOM FOR WHAT’S NEXT", "Important dates.\nIn plain sight.",
        "See subscription windows, expected coupons and maturities. Choose the reminders that matter to you in Settings."),
    REPORTS("YOUR RECORD, WHEN YOU NEED IT", "A year of investing.\nClearly documented.",
        "Keep your documents together and generate an annual report whenever you need one. You choose what to export."),
}

/** First-run presentation never waits for network work or dismisses itself when a session arrives. */
internal fun shouldShowIntroduction(requested: Boolean, locked: Boolean): Boolean = requested && !locked

@Composable
internal fun OnboardingScreen(
    replay: Boolean = false,
    animateBrand: Boolean = false,
    brandFinished: () -> Unit = {},
    continueAsGuest: (() -> Unit)? = null,
    finish: () -> Unit,
) {
    val pages = IntroductionStep.entries
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    val feedback = LocalSelectionFeedback.current
    val signature = remember { Animatable(if (animateBrand) 0f else 1f) }
    var markBounds by remember { mutableStateOf<Rect?>(null) }
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    val finishBrand by rememberUpdatedState(brandFinished)
    LaunchedEffect(animateBrand, reducedMotion) {
        if (animateBrand) {
            // Measure the real welcome artwork before animating so the handoff fits every screen.
            snapshotFlow { markBounds }.filterNotNull().first()
            withFrameNanos { }
            if (reducedMotion) signature.snapTo(1f)
            else signature.animateTo(1f, tween(2_600, easing = LinearEasing))
            finishBrand()
        }
    }
    val welcomeReveal = if (!animateBrand || reducedMotion) 1f else brandWelcomeReveal(signature.value)
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val colors = MaterialTheme.colorScheme
    val background = StructColors.Onboarding.background.resolve(dark)
    val artworkSurface = StructColors.Onboarding.artworkSurface.resolve(dark)
    fun move(page: Int) {
        feedback()
        scope.launch { if (reducedMotion) pager.scrollToPage(page) else pager.animateScrollToPage(page) }
    }
    PlatformBackHandler(true) { if (animateBrand) Unit else if (pager.currentPage > 0) move(pager.currentPage - 1) else finish() }
    Surface(Modifier.fillMaxSize(), color = background, contentColor = colors.onSurface) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { rootPosition = it.positionInRoot() }) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 600.dp).fillMaxSize().graphicsLayer { alpha = welcomeReveal }
                    .then(if (animateBrand) Modifier.clearAndSetSemantics {} else Modifier)) {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp).heightIn(min = 56.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        StructMark(Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        AppText("STRUCT CAPITAL", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold, color = colors.primary)
                        TextButton(onClick = finish) { AppText(if (replay) "Close" else "Skip") }
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val copyFirst = maxHeight < 420.dp
                        val artHeight = (maxHeight * .43f).coerceIn(144.dp, 280.dp)
                        val diagramHeight = (maxWidth - 72.dp) * (290f / 440f) + 24.dp
                        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !animateBrand, beyondViewportPageCount = 1) { index ->
                            val step = pages[index]
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                if (copyFirst) {
                                    IntroductionCopy(step, compact = true)
                                    Spacer(Modifier.height(20.dp))
                                }
                                Box(Modifier.fillMaxWidth().height(if (step == IntroductionStep.IMPORT) diagramHeight else artHeight).clip(RoundedCornerShape(32.dp))
                                    .background(Brush.radialGradient(listOf(colors.primary.copy(alpha = .06f), artworkSurface))),
                                    contentAlignment = Alignment.Center) {
                                    IntroductionArtwork(step, dark,
                                        active = !animateBrand && pager.settledPage == index,
                                        hideOverviewMark = animateBrand && !reducedMotion,
                                        markPlaced = { markBounds = it })
                                }
                                if (!copyFirst) {
                                    Spacer(Modifier.height(24.dp))
                                    IntroductionCopy(step, compact = false)
                                }
                                Spacer(Modifier.height(20.dp))
                            }
                        }
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            pages.forEachIndexed { index, step ->
                                val selected = pager.currentPage == index
                                val label = "${localized("Step")} ${index + 1} ${localized("of")} ${pages.size}: ${localized(step.title).replace('\n', ' ')}"
                                Box(Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Tab) { move(index) }
                                    .semantics { contentDescription = label; this.selected = selected }, contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(width = if (selected) 24.dp else 7.dp, height = 7.dp).clip(CircleShape)
                                        .background(colors.primary.copy(alpha = if (selected) 1f else .22f)))
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (pager.currentPage > 0) OutlinedIconButton(onClick = { move(pager.currentPage - 1) },
                                modifier = Modifier.size(56.dp), shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, colors.primary.copy(alpha = .25f))) {
                                AppIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Previous step")
                            }
                            Button(onClick = { if (pager.currentPage == pages.lastIndex) finish() else move(pager.currentPage + 1) },
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StructColors.Onboarding.action.resolve(dark),
                                    contentColor = StructColors.Onboarding.onAction.resolve(dark)
                                )
                            ) {
                                AppText(if (pager.currentPage == pages.lastIndex) { if (replay) "Done" else "Get started" } else "Continue",
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(10.dp))
                                AppIcon(if (pager.currentPage == pages.lastIndex) Icons.Outlined.Check else Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(19.dp))
                            }
                        }
                        if (!replay && continueAsGuest != null) OutlinedButton(onClick = continueAsGuest, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { AppText("Continue as Guest") }
                        if (!replay) TextButton(onClick = finish) { AppText("Already have an account? Sign in", textAlign = TextAlign.Center) }
                        else AppText("Built by GABO", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            if (animateBrand && !reducedMotion) BrandIntroOverlay(
                progress = signature.value,
                destination = markBounds?.translate(-rootPosition),
            )
        }
    }
}

/** Short viewports lead with readable copy; artwork remains available by scrolling. */
@Composable
private fun IntroductionCopy(step: IntroductionStep, compact: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AppText(step.eyebrow, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
        AppText(step.title, Modifier.semantics { heading() },
            style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
        AppText(step.body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IntroductionArtwork(
    step: IntroductionStep,
    dark: Boolean,
    active: Boolean = false,
    hideOverviewMark: Boolean = false,
    markPlaced: (Rect) -> Unit = {},
) {
    val primary = MaterialTheme.colorScheme.primary
    when (step) {
        IntroductionStep.OVERVIEW -> Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(210.dp).clip(CircleShape).background(primary.copy(alpha = .035f)))
            Surface(Modifier.size(174.dp), shape = CircleShape, color = Color.Transparent,
                border = BorderStroke(1.dp, primary.copy(alpha = .16f))) {}
            StructMark(Modifier.size(138.dp).onGloballyPositioned {
                val position = it.positionInRoot()
                markPlaced(Rect(position.x, position.y, position.x + it.size.width, position.y + it.size.height))
            }.graphicsLayer { alpha = if (hideOverviewMark) 0f else 1f })
        }
        IntroductionStep.IMPORT -> ImportFlowDiagram(dark, active)
        IntroductionStep.CALENDAR -> CalendarReminderArtwork(dark, active)
        IntroductionStep.REPORTS -> ReportAssemblyArtwork(dark, active)
    }
}
