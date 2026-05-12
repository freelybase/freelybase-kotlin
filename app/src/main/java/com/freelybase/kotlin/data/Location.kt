package com.freelybase.kotlin.data

/** Object 类型字段：嵌套对象，直接用 data class */
data class Location(
    val city: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
)