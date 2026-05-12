package com.freelybase.kotlin.data

import io.freelybase.kotlin.FreelyUser

class AppUser : FreelyUser() {
    var nickname: String = ""
    var avatar: String = ""
    var age: Int = 0
    var city: String = ""
}