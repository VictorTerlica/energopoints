package com.example.energopoints.data

import kotlinx.serialization.Serializable

@Serializable
data class Point(
    val code: String = "",
    val type: String = "",
    val name: String = "",
    val district: String = "",
    val latitude: Double,
    val longitude: Double,
    val note: String = "",
    val ownership: String = "ССК",
    val iconColor: String = "#1565C0" // Цвет по умолчанию (синий)
)
