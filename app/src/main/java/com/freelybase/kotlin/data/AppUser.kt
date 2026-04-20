package com.freelybase.kotlin.data

import io.freelybase.kotlin.FBUser

class AppUser : FBUser() {
    var nickname: String = ""
    var avatar: String = ""
    var age: Int = 0
    var city: String = ""
}