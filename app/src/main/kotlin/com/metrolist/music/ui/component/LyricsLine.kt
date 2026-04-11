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
import androidx.compose.runtime.collectAsState
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
            bottom = if (item.isBackground) 2.dp else 12.dp
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

                val romanizedTextState by item.romanizedTextFlow.collectAsState()
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

                val transText by item.translatedTextFlow.collectAsState()
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

    var smoothPosition by remember { mutableLongStateOf(currentPositionState + lyricsOffset) }

    LaunchedEffect(isActiveLine) {
        if (isActiveLine) {
            // Always extrapolate from the last known player position using wall-clock delta.
            // We snapshot playerPos + wallTime together so elapsed is always measured from
            // the same instant the position was sampled — no lag from polling interval.
            var lastSnapPlayerPos = playerConnection.player.currentPosition
            var lastSnapWallMs    = System.currentTimeMillis()
            while (isActive) {
                withFrameMillis {
                    val nowWall   = System.currentTimeMillis()
                    val playerPos = playerConnection.player.currentPosition
                    // If ExoPlayer gave us a new position, re-anchor the snapshot.
                    if (playerPos != lastSnapPlayerPos) {
                        lastSnapPlayerPos = playerPos
                        lastSnapWallMs    = nowWall
                    }
                    // Extrapolate: add wall-clock elapsed only when playing.
                    val elapsed = if (playerConnection.player.isPlaying) nowWall - lastSnapWallMs else 0L
                    smoothPosition = lastSnapPlayerPos + lyricsOffset + elapsed
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
        val wordIdxMap    = IntArray(mainText.length) { -1 }
        val charInWordMap = IntArray(mainText.length) { 0 }
        val wordLenMap    = IntArray(mainText.length) { 1 }
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
                    wordIdxMap[pos]    = wordIdx
                    charInWordMap[pos] = i
                    wordLenMap[pos]    = rawWordText.length
                }
                if (indexInMain + rawWordText.length < mainText.length &&
                    mainText[indexInMain + rawWordText.length] == ' '
                ) {
                    val pos = indexInMain + rawWordText.length
                    wordIdxMap[pos]    = wordIdx
                    charInWordMap[pos] = rawWordText.length
                    wordLenMap[pos]    = rawWordText.length + 1
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
                    val groupSize    = currentGroup.size
                    val groupStartMs = (effectiveWords[currentGroup.first()].startTime * 1000).toLong()
                    val groupEndMs   = (word.endTime * 1000).toLong()
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

        val isRtlText = remember(mainText) { mainText.containsRtl() }

        Canvas(
            modifier = Modifier
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
                return@Canvas
            }

            // ── RTL path ─────────────────────────────────────────────────────────────────────
            if (isRtlText) {
                val (wordIdxMap, charInWordMap, wordLenMap) = charToWordData
                val wordFactors = effectiveWords.map { word ->
                    val wStartMs = (word.startTime * 1000).toLong()
                    val wEndMs   = (word.endTime   * 1000).toLong()
                    val isWordSung   = smoothPosition > wEndMs
                    val isWordActive = smoothPosition in wStartMs..wEndMs
                    val sungFactor = when {
                        isWordSung   -> 1f
                        isWordActive -> ((smoothPosition - wStartMs).toFloat() / (wEndMs - wStartMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                        else         -> 0f
                    }
                    Triple(sungFactor, word, isWordSung)
                }

                // Pass A: base layer
                drawText(layoutResult, color = expressiveAccent.copy(alpha = 0.65f))

                // Pass B: RTL gradient sweep (right → left)
                var sweepFillX = this.size.width
                for (i in mainText.indices) {
                    val wordIdx = wordIdxMap[i]
                    if (wordIdx == -1) continue
                    val (sungFactor, wordItem, isWordSung) = wordFactors[wordIdx]
                    val bounds = layoutResult.getBoundingBox(i)
                    if (isWordSung) {
                        if (bounds.left < sweepFillX) sweepFillX = bounds.left
                    } else if (sungFactor > 0f) {
                        val sMs    = wordItem.startTime * 1000
                        val dur    = (wordItem.endTime * 1000 - sMs).coerceAtLeast(100.0)
                        val wProg  = (smoothPosition.toDouble() - sMs) / dur
                        val charLp = ((wProg - charInWordMap[i].toDouble() / wordLenMap[i].toDouble()) * wordLenMap[i]).coerceIn(0.0, 1.0).toFloat()
                        val charFillX = bounds.right - bounds.width * charLp
                        if (charFillX < sweepFillX) sweepFillX = charFillX
                    }
                }

                val canvasW   = this.size.width
                val avgCharW  = if (mainText.isNotEmpty()) layoutResult.size.width.toFloat() / mainText.length else 20f
                val edgeWidth = (avgCharW * 1.8f).coerceAtLeast(12f)
                val gradStart = sweepFillX.coerceAtLeast(0f)
                val gradEnd   = (sweepFillX + edgeWidth).coerceAtMost(canvasW)

                if (sweepFillX < canvasW) {
                    drawIntoCanvas { canvas ->
                        sweepPaint.textSize = lyricStyle.fontSize.toPx()
                        sweepPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                        if (gradStart >= gradEnd) {
                            sweepPaint.shader = null
                            sweepPaint.color  = expressiveAccent.toArgb()
                        } else if (gradEnd < canvasW) {
                            sweepPaint.shader = android.graphics.LinearGradient(
                                0f, 0f, canvasW, 0f,
                                intArrayOf(
                                    android.graphics.Color.TRANSPARENT,
                                    expressiveAccent.toArgb(),
                                    expressiveAccent.toArgb()
                                ),
                                floatArrayOf(gradStart / canvasW, gradEnd / canvasW, 1f),
                                android.graphics.Shader.TileMode.CLAMP
                            )
                        } else {
                            sweepPaint.shader = android.graphics.LinearGradient(
                                0f, 0f, canvasW, 0f,
                                intArrayOf(
                                    android.graphics.Color.TRANSPARENT,
                                    expressiveAccent.toArgb()
                                ),
                                floatArrayOf(gradStart / canvasW, gradEnd / canvasW),
                                android.graphics.Shader.TileMode.CLAMP
                            )
                        }
                        canvas.nativeCanvas.drawText(
                            mainText, 0, mainText.length,
                            0f, layoutResult.firstBaseline, sweepPaint
                        )
                    }
                }
                return@Canvas
            }

            // ── LTR path ─────────────────────────────────────────────────────────────────────
            val (wordIdxMap, charInWordMap, wordLenMap) = charToWordData
            val wordFactors = effectiveWords.map { word ->
                val wStartMs = (word.startTime * 1000).toLong()
                val wEndMs   = (word.endTime   * 1000).toLong()
                val isWordSung   = smoothPosition > wEndMs
                val isWordActive = smoothPosition in wStartMs..wEndMs
                val sungFactor = when {
                    isWordSung   -> 1f
                    isWordActive -> ((smoothPosition - wStartMs).toFloat() / (wEndMs - wStartMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                    else         -> 0f
                }
                Triple(sungFactor, word, isWordSung)
            }

            val wordWobbles = FloatArray(words.size)
            words.forEachIndexed { wordIdx, word ->
                val startMs        = (word.startTime * 1000).toLong()
                val timeSinceStart = (smoothPosition - startMs).toFloat()
                wordWobbles[wordIdx] = when {
                    timeSinceStart < 0f    -> 0f
                    timeSinceStart < 125f  -> timeSinceStart / 125f
                    timeSinceStart < 750f  -> (1f - (timeSinceStart - 125f) / 625f).coerceAtLeast(0f)
                    else                   -> 0f
                }
            }

            val lineCurrentPushes = FloatArray(layoutResult.lineCount)
            val lineTotalPushes   = FloatArray(layoutResult.lineCount)

            // Pass 1: pre-calculate total scale pushes per line for alignment correction.
            for (i in mainText.indices) {
                val lineIdx         = layoutResult.getLineForOffset(i)
                val wordIdx         = wordIdxMap[i]
                val originalWordIdx = if (wordIdx != -1) effectiveToOriginalIdx[wordIdx] else -1
                val (sungFactor, wordItem, isWordSung) = if (wordIdx != -1) wordFactors[wordIdx] else Triple(0f, null, false)
                val wobble          = if (originalWordIdx != -1) wordWobbles[originalWordIdx] else 0f
                var crescendoDeltaX = 0f
                val groupWord = if (wordIdx != -1) hyphenGroupData[wordIdx] else null
                if (groupWord != null) {
                    val p            = sungFactor
                    val timeSinceEnd = (smoothPosition - groupWord.groupEndMs).toFloat()
                    val pOut         = (timeSinceEnd / 600f).coerceIn(0f, 1f)
                    val peakScale = 0.06f; val decay = 2.5f; val freq = 10.0f; val bsps = 0.012f
                    crescendoDeltaX = if (pOut > 0f)
                        (groupWord.pos * bsps + peakScale) * exp(-decay * pOut) * cos(freq * pOut * PI.toFloat()) * (1f - pOut)
                    else if (groupWord.isLast)
                        groupWord.pos * bsps + peakScale * (1f - exp(-decay * p) * cos(freq * p * PI.toFloat()) * (1f - p))
                    else
                        groupWord.pos * bsps + if (p > 0f) 0.02f * (1f - p) else 0f
                }
                val charLp = if (wordItem != null) {
                    val sMs   = wordItem.startTime * 1000
                    val dur   = (wordItem.endTime * 1000 - sMs).coerceAtLeast(100.0)
                    val wProg = (smoothPosition.toDouble() - sMs) / dur
                    ((wProg - charInWordMap[i].toDouble() / wordLenMap[i].toDouble()) * wordLenMap[i]).coerceIn(0.0, 1.0).toFloat()
                } else 0f
                val nudgeScale  = if (wordItem != null && !isWordSung && sungFactor > 0f) 0.038f * sin(charLp * PI.toFloat()) * exp(-3f * charLp) else 0f
                val charScaleX  = 1f + wobble * 0.025f + crescendoDeltaX + nudgeScale * 0.3f
                lineTotalPushes[lineIdx] += layoutResult.getBoundingBox(i).width * (charScaleX - 1f)
            }

            // Pre-compute per-character scale factors (reused in Pass B sweep + Pass C animation).
            val charScaleXArr = FloatArray(mainText.length)
            val charScaleYArr = FloatArray(mainText.length)
            for (i in mainText.indices) {
                val wordIdx = wordIdxMap[i]
                if (wordIdx == -1) { charScaleXArr[i] = 1f; charScaleYArr[i] = 1f; continue }
                val originalWordIdx = effectiveToOriginalIdx[wordIdx]
                val (sf, wi, iws)   = wordFactors[wordIdx]
                val wobble          = wordWobbles[originalWordIdx]
                val charLp = if (wi != null) {
                    val sMs   = wi.startTime * 1000
                    val dur   = (wi.endTime * 1000 - sMs).coerceAtLeast(100.0)
                    val wProg = (smoothPosition.toDouble() - sMs) / dur
                    ((wProg - charInWordMap[i].toDouble() / wordLenMap[i].toDouble()) * wordLenMap[i]).coerceIn(0.0, 1.0).toFloat()
                } else 0f
                var cdX = 0f; var cdY = 0f
                val gw = hyphenGroupData[wordIdx]
                if (gw != null) {
                    val p    = sf
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
                val nudge        = if (wi != null && !iws && sf > 0f) 0.038f * sin(charLp * PI.toFloat()) * exp(-3f * charLp) else 0f
                charScaleXArr[i] = 1f + wobble * 0.025f + cdX + nudge * 0.3f
                charScaleYArr[i] = 1f + wobble * 0.015f + cdY + nudge
            }

            // ── Pass A: base layer ────────────────────────────────────────────────────────────
            drawText(layoutResult, color = expressiveAccent.copy(alpha = 0.65f))

            // ── Pass B: per-line gradient sweep ───────────────────────────────────────────────
            val canvasW   = this.size.width
            val avgCharW  = if (mainText.isNotEmpty()) layoutResult.size.width.toFloat() / mainText.length else 20f
            val edgeWidth = (avgCharW * 1.8f).coerceAtLeast(12f)

            val lineAlignShifts = FloatArray(layoutResult.lineCount)
            val lineSweepFillX  = FloatArray(layoutResult.lineCount) { 0f }
            lineCurrentPushes.fill(0f)

            for (i in mainText.indices) {
                val lineIdx    = layoutResult.getLineForOffset(i)
                val charBounds = layoutResult.getBoundingBox(i)
                val wordIdx    = wordIdxMap[i]
                val aShift = when (alignment) {
                    TextAlign.Center -> -lineTotalPushes[lineIdx] / 2f
                    TextAlign.Right  -> -lineTotalPushes[lineIdx]
                    else             -> 0f
                }
                lineAlignShifts[lineIdx] = aShift
                if (wordIdx != -1) {
                    val (sungFactor, wordItem, isWordSung) = wordFactors[wordIdx]
                    val charRight = aShift + lineCurrentPushes[lineIdx] + charBounds.right
                    if (isWordSung) {
                        if (charRight > lineSweepFillX[lineIdx]) lineSweepFillX[lineIdx] = charRight
                    } else if (sungFactor > 0f && wordItem != null) {
                        val sMs    = wordItem.startTime * 1000
                        val dur    = (wordItem.endTime * 1000 - sMs).coerceAtLeast(100.0)
                        val wProg  = (smoothPosition.toDouble() - sMs) / dur
                        val charLp = ((wProg - charInWordMap[i].toDouble() / wordLenMap[i].toDouble()) * wordLenMap[i]).coerceIn(0.0, 1.0).toFloat()
                        val charFillX = aShift + lineCurrentPushes[lineIdx] + charBounds.left + charBounds.width * charLp
                        if (charFillX > lineSweepFillX[lineIdx]) lineSweepFillX[lineIdx] = charFillX
                    }
                }
                lineCurrentPushes[lineIdx] += charBounds.width * (charScaleXArr[i] - 1f)
            }

            for (li in 0 until layoutResult.lineCount) {
                val fillX = lineSweepFillX[li]
                if (fillX <= 0f) continue
                val aShift     = lineAlignShifts[li]
                val lineTop    = layoutResult.getLineTop(li)
                val lineBottom = layoutResult.getLineBottom(li)
                val gradStart  = (fillX - edgeWidth).coerceAtLeast(0f)
                val gradEnd    = fillX.coerceAtMost(canvasW)

                // Solid sung portion via drawText clip (correct font, no shader artefacts)
                if (gradStart > 0f) {
                    clipRect(0f, lineTop, gradStart, lineBottom) {
                        drawText(layoutResult, color = expressiveAccent)
                    }
                }

                // Feathered leading edge via nativeCanvas LinearGradient
                if (gradEnd > gradStart) {
                    drawIntoCanvas { canvas ->
                        sweepPaint.textSize = lyricStyle.fontSize.toPx()
                        sweepPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                        sweepPaint.shader   = android.graphics.LinearGradient(
                            gradStart, 0f, gradEnd, 0f,
                            intArrayOf(expressiveAccent.toArgb(), android.graphics.Color.TRANSPARENT),
                            null,
                            android.graphics.Shader.TileMode.CLAMP
                        )
                        canvas.nativeCanvas.save()
                        canvas.nativeCanvas.clipRect(gradStart, lineTop, gradEnd, lineBottom)
                        canvas.nativeCanvas.drawText(
                            mainText, 0, mainText.length,
                            aShift, layoutResult.getLineBaseline(li).toFloat(), sweepPaint
                        )
                        canvas.nativeCanvas.restore()
                    }
                }

                // Entire line sung
                if (gradStart >= gradEnd && fillX >= canvasW) {
                    clipRect(0f, lineTop, canvasW, lineBottom) {
                        drawText(layoutResult, color = expressiveAccent)
                    }
                }
            }

            // ── Pass C: per-character scale/wobble/wave ───────────────────────────────────────
            // Devanagari fix: remove the inner clipRect that was cutting off combining marks
            // and matras. We clear the scaled area first, then draw the full layoutResult
            // translated so the target character lands in the right spot. BlendMode.Src
            // means only the freshly drawn pixels survive — no double-draw artefacts.
            lineCurrentPushes.fill(0f)
            val wallTime = System.currentTimeMillis()

            for (i in mainText.indices) {
                val lineIdx    = layoutResult.getLineForOffset(i)
                val charBounds = layoutResult.getBoundingBox(i)
                val wordIdx    = wordIdxMap[i]
                if (wordIdx == -1) continue

                val charScaleX = charScaleXArr[i]
                val charScaleY = charScaleYArr[i]

                // Skip characters that need no animation — avoids unnecessary GPU work.
                if (charScaleX <= 1.001f && charScaleY <= 1.001f) {
                    lineCurrentPushes[lineIdx] += charBounds.width * (charScaleX - 1f)
                    continue
                }

                val alignShift = when (alignment) {
                    TextAlign.Center -> -lineTotalPushes[lineIdx] / 2f
                    TextAlign.Right  -> -lineTotalPushes[lineIdx]
                    else             -> 0f
                }

                val baseX  = alignShift + lineCurrentPushes[lineIdx] + charBounds.left
                val pivotX = charBounds.width  / 2f
                val pivotY = charBounds.height

                var waveOffset = 0f
                val groupWord = hyphenGroupData[wordIdx]
                if (groupWord != null) {
                    val timeInGroup    = (smoothPosition - groupWord.groupStartMs).toFloat()
                    val timeToGroupEnd = (groupWord.groupEndMs - smoothPosition).toFloat()
                    val waveFade = (timeInGroup / 200f).coerceIn(0f, 1f) *
                                   (timeToGroupEnd / 200f).coerceIn(0f, 1f)
                    if (waveFade > 0.01f)
                        waveOffset = sin(wallTime * 0.006f + i * 0.4f) * 3.24f * waveFade
                }

                // Clear the full scaled footprint so Pass A doesn't peek through the edges.
                val scaledW   = charBounds.width  * charScaleX
                val scaledH   = charBounds.height * charScaleY
                val clearLeft = baseX  + pivotX - scaledW / 2f
                val clearTop  = charBounds.top + pivotY - scaledH
                drawRect(
                    color    = Color.Transparent,
                    topLeft  = Offset(clearLeft, clearTop),
                    size     = Size(scaledW, scaledH),
                    blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                )

                // Draw the full layoutResult shifted so this character sits at the scaled
                // position. No clipRect — Devanagari combining marks extend freely.
                withTransform({
                    translate(baseX, charBounds.top + waveOffset)
                    scale(charScaleX, charScaleY, pivot = Offset(pivotX, pivotY))
                }) {
                    translate(-charBounds.left, -charBounds.top) {
                        drawText(
                            layoutResult,
                            color     = expressiveAccent.copy(alpha = 0.65f),
                            blendMode = androidx.compose.ui.graphics.BlendMode.Src
                        )
                    }
                }

                lineCurrentPushes[lineIdx] += charBounds.width * (charScaleX - 1f)
            }
        }
    }
}
