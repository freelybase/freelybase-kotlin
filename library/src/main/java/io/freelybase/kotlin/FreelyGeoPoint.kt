package io.freelybase.kotlin

import kotlin.math.*

/**
 * 地理位置坐标点，用于存储和查询地理位置信息。
 *
 * 使用示例：
 * ```kotlin
 * // 创建地理位置
 * val location = FBGeoPoint(39.9042, 116.4074)  // 北京天安门
 *
 * // 在数据对象中使用
 * class Store : FBObject("Store") {
 *     var name: String = ""
 *     var location: FBGeoPoint? = null
 * }
 *
 * val store = Store().apply {
 *     name = "星巴克"
 *     location = FBGeoPoint(39.9042, 116.4074)
 * }
 * store.save()
 *
 * // 查询附近的店铺
 * val myLocation = FBGeoPoint(39.9100, 116.4100)
 * FBQuery<Store>()
 *     .near("location", myLocation)
 *     .limit(10)
 *     .find()
 *     .onSuccess { stores ->
 *         stores.forEach { store ->
 *             val distance = myLocation.distanceTo(store.location!!)
 *             Log.d("Store", "${store.name}: ${distance}km")
 *         }
 *     }
 * ```
 *
 * @property latitude 纬度，范围 -90.0 到 90.0
 * @property longitude 经度，范围 -180.0 到 180.0
 */
data class FBGeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude in -90.0..90.0) { "纬度必须在 -90.0 到 90.0 之间，当前值: $latitude" }
        require(longitude in -180.0..180.0) { "经度必须在 -180.0 到 180.0 之间，当前值: $longitude" }
    }

    /**
     * 计算到另一个地理位置的距离（单位：千米）。
     * 使用 Haversine 公式计算球面距离。
     *
     * @param other 目标地理位置
     * @return 距离（千米）
     */
    fun distanceTo(other: FBGeoPoint): Double {
        return distanceTo(other, DistanceUnit.KILOMETERS)
    }

    /**
     * 计算到另一个地理位置的距离。
     *
     * @param other 目标地理位置
     * @param unit 距离单位
     * @return 距离
     */
    fun distanceTo(other: FBGeoPoint, unit: DistanceUnit): Double {
        val earthRadius = when (unit) {
            DistanceUnit.KILOMETERS -> 6371.0
            DistanceUnit.MILES -> 3959.0
            DistanceUnit.RADIANS -> 1.0
        }

        val lat1Rad = Math.toRadians(latitude)
        val lat2Rad = Math.toRadians(other.latitude)
        val deltaLat = Math.toRadians(other.latitude - latitude)
        val deltaLon = Math.toRadians(other.longitude - longitude)

        val a = sin(deltaLat / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * 转换为 Map 格式（用于序列化）。
     */
    internal fun toMap(): Map<String, Double> {
        return mapOf(
            "latitude" to latitude,
            "longitude" to longitude
        )
    }

    override fun toString(): String {
        return "FBGeoPoint(lat=$latitude, lon=$longitude)"
    }

    companion object {
        /**
         * 从 Map 创建 FBGeoPoint。
         */
        @JvmStatic
        fun fromMap(map: Map<*, *>): FBGeoPoint? {
            val lat = (map["latitude"] as? Number)?.toDouble() ?: return null
            val lon = (map["longitude"] as? Number)?.toDouble() ?: return null
            return FBGeoPoint(lat, lon)
        }

        /**
         * 从经纬度字符串创建 FBGeoPoint。
         *
         * @param latLonString 格式："纬度,经度"，例如 "39.9042,116.4074"
         */
        @JvmStatic
        fun fromString(latLonString: String): FBGeoPoint? {
            val parts = latLonString.split(",")
            if (parts.size != 2) return null
            val lat = parts[0].trim().toDoubleOrNull() ?: return null
            val lon = parts[1].trim().toDoubleOrNull() ?: return null
            return FBGeoPoint(lat, lon)
        }
    }
}

/**
 * 距离单位。
 */
enum class DistanceUnit {
    /** 千米 */
    KILOMETERS,
    
    /** 英里 */
    MILES,
    
    /** 弧度 */
    RADIANS
}

/**
 * Gson TypeAdapter：FBGeoPoint <-> JSON
 * 序列化：FBGeoPoint -> {"latitude": 39.9042, "longitude": 116.4074}
 * 反序列化：{...} -> FBGeoPoint
 */
internal class FBGeoPointTypeAdapter : com.google.gson.TypeAdapter<FBGeoPoint>() {
    override fun write(out: com.google.gson.stream.JsonWriter, value: FBGeoPoint?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.beginObject()
            out.name("latitude").value(value.latitude)
            out.name("longitude").value(value.longitude)
            out.endObject()
        }
    }

    override fun read(input: com.google.gson.stream.JsonReader): FBGeoPoint? {
        if (input.peek() == com.google.gson.stream.JsonToken.NULL) {
            input.nextNull()
            return null
        }
        
        var latitude: Double? = null
        var longitude: Double? = null
        
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "latitude" -> latitude = input.nextDouble()
                "longitude" -> longitude = input.nextDouble()
                else -> input.skipValue()
            }
        }
        input.endObject()
        
        return if (latitude != null && longitude != null) {
            FBGeoPoint(latitude, longitude)
        } else {
            null
        }
    }
}
