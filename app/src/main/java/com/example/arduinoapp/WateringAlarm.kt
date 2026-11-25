package com.example.arduinoapp

data class WateringAlarm(
    val hour: Int = 7,
    val minute: Int = 0,
    val enabled: Boolean = true,
    val daysMask: Int = 127,
    val durationSeconds: Int = 3
)
