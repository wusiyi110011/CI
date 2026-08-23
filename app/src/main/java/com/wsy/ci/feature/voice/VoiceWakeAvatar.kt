/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.wsy.ci.feature.voice

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import com.wsy.ci.core.designsystem.CiMotion
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiTheme
import com.wsy.ci.voice.VoiceWakeState
import kotlin.math.PI
import kotlin.math.sin

/**
 * 左下角 AI 头像的唤醒反馈。外环表达语音链路状态，头像缩放表达一次明确的唤醒响应。
 */
@Composable
internal fun VoiceWakeAvatar(
    state: VoiceWakeState,
    @DrawableRes avatarRes: Int,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = CiTheme.reducedMotion
    val shouldAnimate = !reducedMotion && state.needsContinuousAnimation()
    val phase = if (shouldAnimate) {
        val transition = rememberInfiniteTransition(label = "语音头像状态")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = state.animationDuration(),
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Restart,
            ),
            label = "语音头像相位",
        )
        animatedPhase
    } else {
        0f
    }
    val amplitudeTarget = if (!reducedMotion) {
        (state as? VoiceWakeState.Capturing)
            ?.amplitude
            ?.times(AMPLITUDE_VISUAL_GAIN)
            ?.coerceIn(0f, 1f)
            ?: 0f
    } else {
        0f
    }
    val amplitude = if (!reducedMotion) {
        val animatedAmplitude by animateFloatAsState(
            targetValue = amplitudeTarget,
            animationSpec = tween(CiMotion.PRESS),
            label = "语音音量",
        )
        animatedAmplitude
    } else {
        0f
    }
    val avatarScale = when {
        reducedMotion -> 1f
        state is VoiceWakeState.WakeDetected -> 1f + WAKE_SCALE * sin(phase * PI).toFloat()
        state is VoiceWakeState.Capturing -> 1f + CAPTURE_SCALE * amplitude
        else -> 1f
    }
    val ringColor = when (state) {
        VoiceWakeState.Off -> Color.Transparent
        VoiceWakeState.Loading,
        VoiceWakeState.Listening,
        VoiceWakeState.Recognizing,
        -> MaterialTheme.colorScheme.tertiary
        VoiceWakeState.WakeDetected,
        is VoiceWakeState.Capturing,
        -> MaterialTheme.colorScheme.primary
        is VoiceWakeState.Failed -> MaterialTheme.colorScheme.error
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(avatarRes),
            contentDescription = null,
            modifier = Modifier
                .size(CiSizes.navRailAiIcon)
                .scale(avatarScale),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = CiSizes.border.toPx() * RING_STROKE_MULTIPLIER
            val radius = size.minDimension / 2f - stroke
            val statusDotRadius = stroke * STATUS_DOT_RADIUS_MULTIPLIER
            val arcTopLeft = Offset(stroke, stroke)
            val arcSize = Size(size.width - stroke * 2f, size.height - stroke * 2f)
            when (state) {
                VoiceWakeState.Off -> Unit
                VoiceWakeState.Loading -> drawArc(
                    color = ringColor,
                    startAngle = phase * FULL_CIRCLE,
                    sweepAngle = LOADING_ARC,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                VoiceWakeState.Listening -> {
                    drawCircle(
                        color = ringColor.copy(alpha = LISTENING_RING_ALPHA),
                        radius = radius,
                        style = Stroke(width = stroke),
                    )
                    drawCircle(
                        color = ringColor,
                        radius = statusDotRadius,
                        center = Offset(center.x, center.y - radius + statusDotRadius),
                    )
                }
                VoiceWakeState.WakeDetected -> {
                    drawCircle(
                        color = ringColor,
                        radius = radius,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawCircle(
                        color = ringColor.copy(alpha = 1f - phase),
                        radius = radius * (WAKE_RING_START + WAKE_RING_RANGE * phase),
                        style = Stroke(width = stroke),
                    )
                }
                is VoiceWakeState.Capturing -> {
                    val captureStroke = stroke * (CAPTURE_STROKE_BASE + amplitude * CAPTURE_STROKE_RANGE)
                    drawCircle(
                        color = ringColor.copy(
                            alpha = CAPTURE_RING_BASE_ALPHA + amplitude * CAPTURE_RING_ALPHA_RANGE,
                        ),
                        radius = size.minDimension / 2f - captureStroke / 2f,
                        style = Stroke(width = captureStroke, cap = StrokeCap.Round),
                    )
                }
                VoiceWakeState.Recognizing -> {
                    drawCircle(
                        color = ringColor.copy(alpha = RECOGNIZING_TRACK_ALPHA),
                        radius = radius,
                        style = Stroke(width = stroke),
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = phase * FULL_CIRCLE,
                        sweepAngle = RECOGNIZING_ARC,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                is VoiceWakeState.Failed -> {
                    drawCircle(
                        color = ringColor,
                        radius = radius,
                        style = Stroke(width = stroke),
                    )
                    drawCircle(
                        color = ringColor,
                        radius = statusDotRadius,
                        center = Offset(center.x + radius * STATUS_DOT_OFFSET, center.y + radius * STATUS_DOT_OFFSET),
                    )
                }
            }
        }
    }
}

internal fun VoiceWakeState.avatarLabel(fallback: String): String = when (this) {
    VoiceWakeState.Off -> fallback
    VoiceWakeState.Loading -> "准备唤醒"
    VoiceWakeState.Listening -> "待唤醒"
    VoiceWakeState.WakeDetected -> "已听见"
    is VoiceWakeState.Capturing -> "我在听"
    VoiceWakeState.Recognizing -> "理解中"
    is VoiceWakeState.Failed -> "唤醒异常"
}

private fun VoiceWakeState.needsContinuousAnimation(): Boolean =
    this is VoiceWakeState.Loading || this is VoiceWakeState.WakeDetected ||
        this is VoiceWakeState.Recognizing

private fun VoiceWakeState.animationDuration(): Int = when (this) {
    VoiceWakeState.WakeDetected -> CiMotion.STATE
    else -> CiMotion.MILESTONE
}

private const val FULL_CIRCLE = 360f
private const val LOADING_ARC = 110f
private const val RECOGNIZING_ARC = 92f
private const val RING_STROKE_MULTIPLIER = 2f
private const val STATUS_DOT_RADIUS_MULTIPLIER = 1.35f
private const val STATUS_DOT_OFFSET = 0.7f
private const val LISTENING_RING_ALPHA = 0.48f
private const val RECOGNIZING_TRACK_ALPHA = 0.24f
private const val CAPTURE_RING_BASE_ALPHA = 0.58f
private const val CAPTURE_RING_ALPHA_RANGE = 0.42f
private const val CAPTURE_STROKE_BASE = 1f
private const val CAPTURE_STROKE_RANGE = 1.6f
private const val WAKE_RING_START = 0.9f
private const val WAKE_RING_RANGE = 0.1f
private const val WAKE_SCALE = 0.08f
private const val CAPTURE_SCALE = 0.035f
private const val AMPLITUDE_VISUAL_GAIN = 12f
