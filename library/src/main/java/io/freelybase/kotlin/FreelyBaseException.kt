package io.freelybase.kotlin

class FreelyBaseException(
    message: String,
    val statusCode: Int = 0,
    val detail: String = message
) : Exception(message) {
    override fun toString() = "FreelyBaseException[$statusCode]: $detail"
}
