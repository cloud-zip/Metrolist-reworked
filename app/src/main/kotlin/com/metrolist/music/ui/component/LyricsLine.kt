/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.lyrics.LyricsEntry
import com.metrolist.music.lyrics.WordTimestamp
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.ui.screens.settings.LyricsPosition
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.PI

private data class HyphenGroupWord(
    val pos: Int,
    val size: Int,
    val isLast: Boolean,
    val groupStartMs: Long,
    val groupEndMs: Long
)

private fun String.containsRtl(): Boolean {
    for (c in this) {
        val directionality = Character.getDirectionality(c).toInt()
        if (directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT.toInt() ||
            directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC.toInt()
        ) {
            return true
        }
    }
    return false
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LyricsLine(
    index: Int,
    item: LyricsEntry,
    isSynced: Boolean,
    isActiveLine: Boolean,
    bgVisible: Boolean,
    isSelected: Boolean,
    isSelectionModeActive: Boolean,
    currentPositionState: Long,
    lyricsOffset: Long,
    playerConnection: PlayerConnection,
    lyricsTextSize: Float,
    lyricsLineSpacing: Float,
    expressiveAccent: Color,
    lyricsTextPosition: LyricsPosition,
    respectAgentPositioning: Boolean,
    isAutoScrollEnabled: Boolean,
    displayedCurrentLineIndex: Int,
    romanizeAsMain: Boolean,
    enabledLanguages: List<String>,
    romanizeLyrics: Boolean,
    onSizeChanged: (Int) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    val itemModifier = modifier
        .fillMaxWidth()
        .onSizeChanged { onSizeChanged(it.height) }
        .clip(RoundedCornerShape(8.dp))
        .combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
        .background(if (isSelected && isSelectionModeActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent)
        .padding(
            start = when (lyricsTextPosition) { LyricsPosition.LEFT, LyricsPosition.RIGHT -> 11.dp; LyricsPosition.CENTER -> 24.dp },
            end = when (lyricsTextPosition) { LyricsPosition.LEFT, LyricsPosition.RIGHT -> 11.dp; LyricsPosition.CENTER -> 24.dp },
            top = if (item.isBackground) 0.dp else 12.dp,
            bottom = if (item.isBackground) 2.dp else 12.dp // simplified gap logic
        )

    val agentAlignment = when {
        respectAgentPositioning && item.agent == "v1" -> Alignment.Start
        respectAgentPositioning && item.agent == "v2" -> Alignment.End
        respectAgentPositioning && item.agent == "v1000" -> Alignment.CenterHorizontally
        item.isBackground -> Alignment.CenterHorizontally
        else -> when (lyricsTextPosition) {
            LyricsPosition.LEFT -> Alignment.Start
            LyricsPosition.CENTER -> Alignment.CenterHorizontally
            LyricsPosition.RIGHT -> Alignment.End
        }
    }
    
    val agentTextAlign = when {
        respectAgentPositioning && item.agent == "v1" -> TextAlign.Left
        respectAgentPositioning && item.agent == "v2" -> TextAlign.Right
        respectAgentPositioning && item.agent == "v1000" -> TextAlign.Center
        item.isBackground -> TextAlign.Center
        else -> when (lyricsTextPosition) {
            LyricsPosition.LEFT -> TextAlign.Left
            LyricsPosition.CENTER -> TextAlign.Center
            LyricsPosition.RIGHT -> TextAlign.Right
        }
    }

    Box(modifier = itemModifier, contentAlignment = when {
        respectAgentPositioning && item.agent == "v1" -> Alignment.CenterStart
        respectAgentPositioning && item.agent == "v2" -> Alignment.CenterEnd
        item.isBackground -> Alignment.Center
        respectAgentPositioning && item.agent == "v1000" -> Alignment.Center
        else -> when (lyricsTextPosition) {
            LyricsPosition.LEFT -> Alignment.CenterStart
            LyricsPosition.RIGHT -> Alignment.CenterEnd
            LyricsPosition.CENTER -> Alignment.Center
        }
    }) {
        @Composable
        fun LyricContent() {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = agentAlignment) {
                val inactiveAlpha = if (item.isBackground) 0.08f else 0.2f
                val activeAlpha = 1f
                val focusedAlpha = if (item.isBackground) 0.5f else 0.3f
                val targetAlpha = if (item.isBackground || isActiveLine) {
                    activeAlpha
                } else if (isAutoScrollEnabled && displayedCurrentLineIndex >= 0) {
                    when (abs(index - displayedCurrentLineIndex)) {
                        0 -> focusedAlpha
                        1 -> 0.2f; 2 -> 0.2f; 3 -> 0.15f; 4 -> 0.1f; else -> 0.08f
                    }
                } else inactiveAlpha
                
                val animatedAlpha by animateFloatAsState(targetAlpha, tween(250), label = "lyricsLineAlpha")
                val lineColor = expressiveAccent.copy(alpha = if (item.isBackground) focusedAlpha else animatedAlpha)
                
                val romanizedTextState by item.romanizedTextFlow.collectAsStateWithLifecycle()
                val isRomanizedAvailable = romanizedTextState != null
                val mainTextRaw = if (romanizeAsMain && isRomanizedAvailable) romanizedTextState else item.text
                val subTextRaw = if (romanizeAsMain && isRomanizedAvailable) item.text else romanizedTextState
                val mainText = if (item.isBackground) mainTextRaw?.removePrefix("(")?.removeSuffix(")") else mainTextRaw
                val subText = if (item.isBackground) subTextRaw?.removePrefix("(")?.removeSuffix(")") else subTextRaw

                val lyricStyle = TextStyle(
                    fontSize = if (item.isBackground) (lyricsTextSize * 0.7f).sp else lyricsTextSize.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = if (item.isBackground) FontStyle.Italic else FontStyle.Normal,
                    lineHeight = if (item.isBackground) (lyricsTextSize * 0.7f * lyricsLineSpacing).sp else (lyricsTextSize * lyricsLineSpacing).sp,
                    letterSpacing = (-0.5).sp,
                    textAlign = agentTextAlign,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both
                    )
                )

                val effectiveWords = if (item.words?.isNotEmpty() == true) item.words else null

                if (isSynced && effectiveWords != null && (isActiveLine || abs(index - displayedCurrentLineIndex) <= 3) && mainText != null) {
                    WordLevelLyrics(
                        mainText = mainText,
                        words = effectiveWords,
                        isActiveLine = isActiveLine,
                        currentPositionState = currentPositionState,
                        lyricsOffset = lyricsOffset,
                        playerConnection = playerConnection,
                        lyricStyle = lyricStyle,
                        lineColor = lineColor,
                        expressiveAccent = expressiveAccent,
                        isBackground = item.isBackground,
                        focusedAlpha = focusedAlpha,
                        alignment = agentTextAlign
                    )
                } else {
                    Text(
                        text = mainText ?: "",
                        style = lyricStyle.copy(color = if (isActiveLine) expressiveAccent else lineColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                if (romanizeLyrics && enabledLanguages.isNotEmpty()) {
                    subText?.let { 
                        Text(
                            text = it,
                            fontSize = 18.sp,
                            color = expressiveAccent.copy(alpha = 0.6f),
                            textAlign = agentTextAlign,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                
                val transText by item.translatedTextFlow.collectAsStateWithLifecycle()
                transText?.let { 
                    Text(
                        text = it,
                        fontSize = 16.sp,
                        color = expressiveAccent.copy(alpha = 0.5f),
                        textAlign = agentTextAlign,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        if (item.isBackground) {
            AnimatedVisibility(
                visible = bgVisible,
                enter = fadeIn(tween(durationMillis = 250, delayMillis = 100)),
                exit = fadeOut(tween(250))
            ) {
                LyricContent()
            }
        } else {
            LyricContent()
        }
    }
}

@Composable
private fun WordLevelLyrics(
    mainText: String,
    words: List<WordTimestamp>,
    isActiveLine: Boolean,
    currentPositionState: Long,
    lyricsOffset: Long,
    playerConnection: PlayerConnection,
    lyricStyle: TextStyle,
    lineColor: Color,
    expressiveAccent: Color,
    isBackground: Boolean,
    focusedAlpha: Float,
    alignment: TextAlign
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val sweepPaint = remember {
        android.graphics.Paint().apply { isAntiAlias = true }
    }
    
    var smoothPosition by remember { mutableLongStateOf(playerConnection.player.currentPosition + lyricsOffset) }
    
    LaunchedEffect(isActiveLine) {
        if (isActiveLine) {
            // Snap immediately to current position before the polling loop starts,
            // eliminating the one-frame lag from stale currentPositionState.
            smoothPosition = playerConnection.player.currentPosition + lyricsOffset
            var lastPlayerPos = playerConnection.player.currentPosition
            var lastUpdateTime = System.currentTimeMillis()
            while (isActive) {
                withFrameMillis {
                    val now = System.currentTimeMillis()
                    val playerPos = playerConnection.player.currentPosition
                    if (playerPos != lastPlayerPos) {
                        lastPlayerPos = playerPos
                        lastUpdateTime = now
                    }
                    val elapsed = now - lastUpdateTime
                    smoothPosition = lastPlayerPos + lyricsOffset + (if (playerConnection.player.isPlaying) elapsed else 0)
                }
            }
        }
    }
    
    LaunchedEffect(isActiveLine, currentPositionState) {
        if (!isActiveLine) {
            smoothPosition = currentPositionState + lyricsOffset
        }
    }

    val (effectiveWords, effectiveToOriginalIdx) = remember(words, isBackground) {
        words.flatMapIndexed { originalIdx, word ->
            val shouldSplit = word.text.contains('-') && word.text.length > 1 &&
                (!word.hasTrailingSpace || words.size == 1)
            if (shouldSplit) {
                val segments = mutableListOf<String>()
                var start = 0
                for (i in 0 until word.text.length) {
                    if (word.text[i] == '-') {
                        segments.add(word.text.substring(start, i + 1))
                        start = i + 1
                    }
                }
                if (start < word.text.length) {
                    segments.add(word.text.substring(start))
                }

                if (segments.size > 1) {
                    val totalDuration = word.endTime - word.startTime
                    val segmentDuration = totalDuration / segments.size
                    segments.mapIndexed { index, segmentText ->
                        WordTimestamp(
                            text = segmentText,
                            startTime = word.startTime + index * segmentDuration,
                            endTime = word.startTime + (index + 1) * segmentDuration,
                            hasTrailingSpace = if (index == segments.size - 1) word.hasTrailingSpace else false
                        ) to originalIdx
                    }
                } else listOf(word to originalIdx)
            } else listOf(word to originalIdx)
        }.let { data -> data.map { it.first } to data.map { it.second } }
    }

    val charToWordData = remember(mainText, effectiveWords, isBackground) {
        val wordIdxMap = IntArray(mainText.length) { -1 }
        val charInWordMap = IntArray(mainText.length) { 0 }
        val wordLenMap = IntArray(mainText.length) { 1 }
        var currentPos = 0
        effectiveWords.forEachIndexed { wordIdx, word ->
            val rawWordText = word.text.let { 
                if (isBackground) {
                    var t = it
                    if (wordIdx == 0) t = t.removePrefix("(")
                    if (wordIdx == effectiveWords.size - 1) t = t.removeSuffix(")")
                    t
                } else it
            }
            val indexInMain = mainText.indexOf(rawWordText, currentPos)
            if (indexInMain != -1) {
                for (i in 0 until rawWordText.length) {
                    val pos = indexInMain + i
                    wordIdxMap[pos] = wordIdx
                    charInWordMap[pos] = i
                    wordLenMap[pos] = rawWordText.length
                }
                if (indexInMain + rawWordText.length < mainText.length && mainText[indexInMain + rawWordText.length] == ' ') {
                    val pos = indexInMain + rawWordText.length
                    wordIdxMap[pos] = wordIdx
                    charInWordMap[pos] = rawWordText.length
                    wordLenMap[pos] = rawWordText.length + 1
                }
                currentPos = indexInMain + rawWordText.length
            }
        }
        Triple(wordIdxMap, charInWordMap, wordLenMap)
    }

    val hyphenGroupData = remember(effectiveWords) {
        val map = mutableMapOf<Int, HyphenGroupWord>()
        var currentGroup = mutableListOf<Int>()
        effectiveWords.forEachIndexed { wordIdx, word ->
            currentGroup.add(wordIdx)
            if (!word.text.endsWith("-")) {
                if (currentGroup.size > 1) {
                    val groupSize = currentGroup.size
                    val groupStartMs = (effectiveWords[currentGroup.first()].startTime * 1000).toLong()
                    val groupEndMs = (word.endTime * 1000).toLong()
                    currentGroup.forEachIndexed { pos, idx ->
                        map[idx] = HyphenGroupWord(pos, groupSize, pos == groupSize - 1, groupStartMs, groupEndMs)
                    }
                }
                currentGroup = mutableListOf()
            }
        }
        map
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth
        val layoutResult = remember(mainText, maxWidthPx, lyricStyle) {
            textMeasurer.measure(
                text = mainText,
                style = lyricStyle,
                constraints = Constraints(minWidth = maxWidthPx, maxWidth = maxWidthPx),
                softWrap = true
            )
        }
        
        // letterLayouts removed: measuring each character in isolation breaks complex scripts
        // (Devanagari, Bengali, Arabic, etc.) because combining marks lose their base-char context.
        // We now draw characters by clipping the correctly-shaped full layoutResult instead.
        
        val isRtlText = remember(mainText) { mainText.containsRtl() }
        
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { layoutResult.size.height.toDp() })
            .graphicsLayer(clip = false)
            .then(Modifier.graphicsLayer {
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            })
        ) {
            if (mainText.isEmpty()) return@Canvas
            if (!isActiveLine) {
                drawText(layoutResult, color = lineColor)
            } else {
                if (isRtlText) {
                    val (wordIdxMap, charInWordMap, wordLenMap) = charToWordData
                    val wordFactors = effectiveWords.map { word ->
                        val wStartMs = (word.startTime * 1000).toLong()
                        val wEndMs = (word.endTime * 1000).toLong()
                        val isWordSung = smoothPosition > wEndMs
                        val isWordActive = smoothPosition in wStartMs..wEndMs
                        val sungFactor = if (isWordSung) 1f
                                        else if (isWordActive) ((smoothPosition - wStartMs).toFloat() / (wEndMs - wStartMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                                        else 0f
                        Triple(sungFactor, word, isWordSung)
                    }

                    // Pass A: base layer
                    drawText(layoutResult, color = expressiveAccent.copy(alpha = 0.65f))

                    // Pass B: RTL sweep — front moves right→left, so sweepX is the leftmost x reached.
                    var sweepX = this.size.width
                    for (i in mainText.indices) {
                        val wordIdx = wordIdxMap[i]
                        if (wordIdx == -1) continue
                        val (sungFactor, wordItem, isWordSung) = wordFactors[wordIdx]
                        val bounds = layoutResult.getBoundingBox(i)
                        val x = if (isWordSung) {
                            bounds.left
                        } else if (sungFactor > 0f && wordItem != null) {
                            val sMs = wordItem.startTime * 1000
                            val dur = (wordItem.endTime * 1000 - sMs).coerceAtLeast(100.0)
                            val wProg = (smoothPosition.toDouble() - sMs) / dur
                            val charLp = ((wProg - charInWordMap[i].toDouble() / wordLenMap[i].toDouble()) * wordLenMap[i]).coerceIn(0.0, 1.0).toFloat()
                            bounds.right - bounds.width * charLp
                        } else Float.MAX_VALUE
                        if (x < sweepX) sweepX = x
                    }

                    val canvasW  = this.size.width
                    val avgCharW = if (mainText.isNotEmpty()) layoutResult.size.width.toFloat() / mainText.length else 20f
                    val edgeWidth = (avgCharW * 2.2f).coerceAtLeast(14f)
                    val lineTop    = layoutResult.getLineTop(0)
                    val lineBottom = layoutResult.getLineBottom(layoutResult.lineCount - 1)
                    val gradEnd   = (sweepX + edgeWidth).coerceAtMost(canvasW)

                    if (sweepX < canvasW) {
                        // Solid sung region (right of feather edge)
                        if (gradEnd < canvasW) {
                            clipRect(gradEnd, lineTop, canvasW, lineBottom) {
                                drawText(layoutResult, color = expressiveAccent)
                            }
                        }
                        // Gradient feather edge via saveLayer — RTL: transparent→opaque left→right
                        sweepPaint.shader = android.graphics.LinearGradient(
                            sweepX.coerceAtLeast(0f), 0f, gradEnd, 0f,
                            intArrayOf(android.graphics.Color.TRANSPARENT, expressiveAccent.toArgb()),
                            null,
                            android.graphics.Shader.TileMode.CLAMP
                        )
                        drawIntoCanvas { canvas ->
                            val saveCount = canvas.nativeCanvas.saveLayer(
                                0f, lineTop, gradEnd, lineBottom, sweepPaint
                            )
                            clipRect(0f, lineTop, gradEnd, lineBottom) {
                                drawText(layoutResult, color = expressiveAccent)
                            }
                            canvas.nativeCanvas.restoreToCount(saveCount)
                        }
                    }
                    return@Canvas
                }

                val (wordIdxMap, charInWordMap, wordLenMap) = charToWordData
                val wordFactors = effectiveWords.map { word ->
                    val wStartMs = (word.startTime * 1000).toLong()
                    val wEndMs = (word.endTime * 1000).toLong()
                    val isWordSung = smoothPosition > wEndMs
                    val isWordActive = smoothPosition in wStartMs..wEndMs
                    val sungFactor = if (isWordSung) 1f
                                    else if (isWordActive) ((smoothPosition - wStartMs).toFloat() / (wEndMs - wStartMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                                    else 0f
                    Triple(sungFactor, word, isWordSung)
                }

                val wordWobbles = FloatArray(words.size)
                words.forEachIndexed { wordIdx, word ->
                    val startMs = (word.startTime * 1000).toLong()
                    val timeSinceStart = (smoothPosition - startMs).toFloat()
                    val wobble = if (timeSinceStart in 0f..750f) {
                        if (timeSinceStart < 125f) timeSinceStart / 125f
                        else (1f - (timeSinceStart - 125f) / 625f).coerceAtLeast(0f)
                    } else 0f
                    wordWobbles[wordIdx] = wobble
                }

                val lineCurrentPushes = FloatArray(layoutResult.lineCount)
                val lineTotalPushes = FloatArray(layoutResult.lineCount)

                // Pass 1: pre-calculate total scale pushes per line for alignment correction.
                for (i in mainText.indices) {
                    val lineIdx = layoutResult.getLineForOffset(i)
                    val wordIdx = wordIdxMap[i]
                    val originalWordIdx = if (wordIdx != -1) effectiveToOriginalIdx[wordIdx] else -1
                    val (sungFactor, wordItem, isWordSung) = if (wordIdx != -1) wordFactors[wordIdx] else Triple(0f, null, false)
                    val wobble = if (originalWordIdx != -1) wordWobbles[originalWordIdx] else 0f
                    var crescendoDeltaX = 0f
                    val groupWord = if (wordIdx != -1) hyphenGroupData[wordIdx] else null
                    if (groupWord != null) {
                        val p = sungFactor
                        val timeSinceEnd = (smoothPosition - groupWord.groupEndMs).toFloat()
                        val pOut = (timeSinceEnd / 600f).coerceIn(0f, 1f)
                        val peakScale = 0.06f; val decay = 2.5f; val freq = 10.0f; val baseScalePerSegment = 0.012f
                        if (pOut > 0f) {
                            crescendoDeltaX = (groupWord.pos * baseScalePerSegment + peakScale) * exp(-decay * pOut) * cos(freq * pOut * PI.toFloat()) * (1f - pOut)
                        } else if (groupWord.isLast) {
                            crescendoDeltaX = groupWord.pos * baseScalePerSegment + peakScale * (1f - exp(-decay * p) * cos(freq * p * PI.toFloat()) * (1f - p))
                        } else {
                            crescendoDeltaX = groupWord.pos * baseScalePerSegment + if (p > 0f) 0.02f * (1f - p) else 0f
                        }
                    }
                    val charLp = if (wordItem != null) {
                        val sMs = wordItem.startTime * 1000
                        val dur = (wordItem.endTime * 1000 - wordItem.startTime * 1000).coerceAtLeast(100.0)
                        val wProg = (smoothPosition.toDouble() - sMs) / dur
                        ((wProg - charInWordMap[i].toDouble() / wordLenMap[i].toDouble()) * wordLenMap[i].toDouble()).coerceIn(0.0, 1.0).toFloat()
                    } else 0f
                    val nudgeScale = if (wordItem != null && !isWordSung && sungFactor > 0f) 0.038f * sin(charLp * PI.toFloat()) * exp(-3f * charLp) else 0f
                    val charScaleX = 1f + (wobble * 0.025f) + crescendoDeltaX + (nudgeScale * 0.3f)
                    lineTotalPushes[lineIdx] += layoutResult.getBoundingBox(i).width * (charScaleX - 1f)
                }

                // ── Apple Music sweep approach ────────────────────────────────────────────────
                // Draw the active line in two passes:
                //
                //  Pass A — base: one drawText at ~65% white. The whole active line is bright
                //           and legible, matching Apple Music's "dim grey" for upcoming words.
                //
                //  Pass B — sweep: one drawText with a single LinearGradient shader that spans
                //           the full line width. The gradient is:
                //             [0 .. fillX-edgeW]   → full white (sung)
                //             [fillX-edgeW .. fillX] → white→transparent (soft leading edge)
                //             [fillX .. lineWidth]   → transparent (base layer shows through)
                //           This is exactly one GPU composite per line — no per-word loops,
                //           no clip slices, no seams. Complex-script shaping is preserved
                //           because layoutResult was measured as one complete string.
                //
                //  Pass C — animation: per-character scale/wobble/wave withTransform, only for
                //           chars that are actually scaled (> 1.001). These run on top of the
                //           sweep layer and also draw via the full layoutResult, so Devanagari
                //           combining marks always have their full shaping context.
                // ─────────────────────────────────────────────────────────────────────────────

                // Pre-compute charScaleX for every character once (reused in both sweep and Pass C).
                val charScaleXArr = FloatArray(mainText.length)
                val charScaleYArr = FloatArray(mainText.length)
                for (i in mainText.indices) {
                    val wordIdx = wordIdxMap[i]
                    if (wordIdx == -1) { charScaleXArr[i] = 1f; charScaleYArr[i] = 1f; continue }
                    val originalWordIdx = effectiveToOriginalIdx[wordIdx]
                    val (sf, wi, iws) = wordFactors[wordIdx]
                    val wobble = wordWobbles[originalWordIdx]
                    val charLp = if (wi != null) {
                        val sMs = wi.startTime * 1000
                        val dur = (wi.endTime * 1000 - sMs).coerceAtLeast(100.0)
                        val wProg = (smoothPosition.toDouble() - sMs) / dur
                        ((wProg - charInWordMap[i].toDouble() / wordLenMap[i].toDouble()) * wordLenMap[i]).coerceIn(0.0, 1.0).toFloat()
                    } else 0f
                    var cdX = 0f; var cdY = 0f
                    val gw = hyphenGroupData[wordIdx]
                    if (gw != null) {
                        val p = sf
                        val pOut = ((smoothPosition - gw.groupEndMs).toFloat() / 600f).coerceIn(0f, 1f)
                        val peakScale = 0.06f; val decay = 3.5f; val freq = 5.0f; val bsps = 0.012f
                        val v = if (pOut > 0f)
                            (gw.pos * bsps + peakScale) * exp(-decay * pOut) * cos(freq * pOut * PI.toFloat()) * (1f - pOut)
                        else if (gw.isLast)
                            gw.pos * bsps + peakScale * (1f - exp(-decay * p) * cos(freq * p * PI.toFloat()) * (1f - p))
                        else
                            gw.pos * bsps + if (p > 0f) 0.02f * (1f - p) else 0f
                        cdX = v; cdY = v
                    }
                    val nudge = if (wi != null && !iws && sf > 0f) 0.038f * sin(charLp * PI.toFloat()) * exp(-3f * charLp) else 0f
                    charScaleXArr[i] = 1f + wobble * 0.025f + cdX + nudge * 0.3f
                    charScaleYArr[i] = 1f + wobble * 0.015f + cdY + nudge
                }

                // (Per-line sweep fill positions are computed inside Pass B below.)

                // ── Pass A: base layer ────────────────────────────────────────────────────────
                drawText(layoutResult, color = expressiveAccent.copy(alpha = 0.65f))

                // ── Pass B: smooth gradient sweep ────────────────────────────────────────────
                // Compute a single continuous sweepX per text line.
                // sweepX moves smoothly through the line at the rate of the music position
                // within the current word — not word-by-word jumps, but frame-perfect continuity.
                //
                // For each text line we compute:
                //   lineSweepX[li]  = canvas x position of the sweep front
                //   lineAlignShifts[li] = alignment shift for drawText origin correction
                //
                // Then for each line with any sung content we draw:
                //   1. Solid accent region (0 → sweepX - edgeW) via clipRect + drawText(layoutResult)
                //   2. Gradient feather (sweepX - edgeW → sweepX) via saveLayer on the full line
                //      so that drawText(layoutResult) inside uses Compose's ICU shaping engine —
                //      Devanagari/Bengali combining marks are always correctly shaped.
                //
                // The saveLayer covers the ENTIRE line (not just the edge), which means one
                // offscreen allocation per line rather than one per feather region. The gradient
                // paint's CLAMP mode makes everything left of gradStart opaque and everything
                // right of gradEnd transparent, so the layer composites correctly with Pass A.

                val canvasW   = this.size.width
                val avgCharW  = if (mainText.isNotEmpty()) layoutResult.size.width.toFloat() / mainText.length else 20f
                val edgeWidth = (avgCharW * 2.2f).coerceAtLeast(14f)

                val lineAlignShifts = FloatArray(layoutResult.lineCount)
                val lineSweepX      = FloatArray(layoutResult.lineCount) { -1f } // -1 = nothing sung yet
                lineCurrentPushes.fill(0f)

                for (i in mainText.indices) {
                    val lineIdx    = layoutResult.getLineForOffset(i)
                    val charBounds = layoutResult.getBoundingBox(i)
                    val wordIdx    = wordIdxMap[i]
                    val aShift = when (alignment) {
                        TextAlign.Center -> -lineTotalPushes[lineIdx] / 2f
                        TextAlign.Right  -> -lineTotalPushes[lineIdx]
                        else -> 0f
                    }
                    lineAlignShifts[lineIdx] = aShift

                    if (wordIdx != -1) {
                        val (sungFactor, wordItem, isWordSung) = wordFactors[wordIdx]
                        // Smooth continuous x: for sung chars use right edge,
                        // for the currently-active char interpolate within it.
                        val x = if (isWordSung) {
                            aShift + lineCurrentPushes[lineIdx] + charBounds.right
                        } else if (sungFactor > 0f && wordItem != null) {
                            val sMs = wordItem.startTime * 1000
                            val dur = (wordItem.endTime * 1000 - sMs).coerceAtLeast(100.0)
                            val wProg = (smoothPosition.toDouble() - sMs) / dur
                            val charLp = ((wProg - charInWordMap[i].toDouble() / wordLenMap[i].toDouble()) * wordLenMap[i]).coerceIn(0.0, 1.0).toFloat()
                            aShift + lineCurrentPushes[lineIdx] + charBounds.left + charBounds.width * charLp
                        } else -1f
                        if (x > lineSweepX[lineIdx]) lineSweepX[lineIdx] = x
                    }
                    lineCurrentPushes[lineIdx] += charBounds.width * (charScaleXArr[i] - 1f)
                }

                for (li in 0 until layoutResult.lineCount) {
                    val sweepX = lineSweepX[li]
                    if (sweepX < 0f) continue // nothing sung on this line yet

                    val lineTop    = layoutResult.getLineTop(li)
                    val lineBottom = layoutResult.getLineBottom(li)
                    val gradStart  = (sweepX - edgeWidth).coerceAtLeast(0f)

                    // Solid fully-sung region: one drawText clipped to the solid portion.
                    // Uses drawText(layoutResult) — correct font, correct complex-script shaping.
                    if (gradStart > 0f) {
                        clipRect(0f, lineTop, gradStart, lineBottom) {
                            drawText(layoutResult, color = expressiveAccent)
                        }
                    }

                    // Gradient feather + remainder: one saveLayer over the full line width.
                    // The gradient paint: opaque from gradStart → sweepX, transparent beyond.
                    // CLAMP mode fills left side solid and right side transparent automatically.
                    // One saveLayer per line = one offscreen buffer per line, not per frame edge.
                    sweepPaint.shader = android.graphics.LinearGradient(
                        gradStart, 0f, sweepX.coerceAtMost(canvasW), 0f,
                        intArrayOf(expressiveAccent.toArgb(), android.graphics.Color.TRANSPARENT),
                        null,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    drawIntoCanvas { canvas ->
                        val saveCount = canvas.nativeCanvas.saveLayer(
                            gradStart, lineTop, canvasW, lineBottom, sweepPaint
                        )
                        clipRect(gradStart, lineTop, canvasW, lineBottom) {
                            drawText(layoutResult, color = expressiveAccent)
                        }
                        canvas.nativeCanvas.restoreToCount(saveCount)
                    }
                }

                // ── Pass C: per-character animation (scale/wobble/wave) ───────────────────────
                // Uses charScaleXArr/charScaleYArr computed above — no duplicate math.
                lineCurrentPushes.fill(0f)
                val wallTime = System.currentTimeMillis()

                for (i in mainText.indices) {
                    val lineIdx    = layoutResult.getLineForOffset(i)
                    val charBounds = layoutResult.getBoundingBox(i)
                    val wordIdx    = wordIdxMap[i]
                    if (wordIdx == -1) continue

                    val charScaleX = charScaleXArr[i]
                    val charScaleY = charScaleYArr[i]
                    val alignShift = when (alignment) {
                        TextAlign.Center -> -lineTotalPushes[lineIdx] / 2f
                        TextAlign.Right  -> -lineTotalPushes[lineIdx]
                        else -> 0f
                    }

                    val baseX  = alignShift + lineCurrentPushes[lineIdx] + charBounds.left
                    val pivotX = charBounds.width / 2f
                    val pivotY = charBounds.height

                    var waveOffset = 0f
                    val groupWord = hyphenGroupData[wordIdx]
                    if (groupWord != null) {
                        val timeInGroup    = (smoothPosition - groupWord.groupStartMs).toFloat()
                        val timeToGroupEnd = (groupWord.groupEndMs - smoothPosition).toFloat()
                        val waveFade = (timeInGroup / 200f).coerceIn(0f, 1f) * (timeToGroupEnd / 200f).coerceIn(0f, 1f)
                        if (waveFade > 0.01f)
                            waveOffset = sin(wallTime * 0.006f + i * 0.4f) * 3.24f * waveFade
                    }

                    if (charScaleX > 1.001f || charScaleY > 1.001f) {
                        // Bug 3 fix: expand the clear rect to cover the full scaled bounds,
                        // not just the unscaled charBounds. Without this, Pass A's static copy
                        // peeks out around the edges of the scaled character.
                        // Scaled size around bottom-centre pivot:
                        val scaledW    = charBounds.width  * charScaleX
                        val scaledH    = charBounds.height * charScaleY
                        val clearLeft  = baseX  + pivotX - scaledW / 2f
                        val clearTop   = charBounds.top + pivotY - scaledH
                        drawRect(
                            color = Color.Transparent,
                            topLeft = Offset(clearLeft, clearTop),
                            size = Size(scaledW, scaledH),
                            blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                        )
                        withTransform({
                            translate(baseX, charBounds.top + waveOffset)
                            scale(charScaleX, charScaleY, pivot = Offset(pivotX, pivotY))
                        }) {
                            clipRect(0f, 0f, charBounds.width, charBounds.height) {
                                translate(-charBounds.left, -charBounds.top) {
                                    drawText(
                                        layoutResult,
                                        color = expressiveAccent.copy(alpha = 0.65f),
                                        blendMode = androidx.compose.ui.graphics.BlendMode.Src
                                    )
                                }
                            }
                        }
                    }
                    lineCurrentPushes[lineIdx] += charBounds.width * (charScaleX - 1f)
                }

            }
        }
    }
}
