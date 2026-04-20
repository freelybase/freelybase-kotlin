package com.freelybase.kotlin.data

import io.freelybase.kotlin.FBFile
import io.freelybase.kotlin.FBObject
import io.freelybase.kotlin.Pointer
import io.freelybase.kotlin.Relation


/** Demo 数据对象，对应后台 Post 表 */
class Post(
    // 基础类型
    var title: String = "",
    var count: Int = 0,
    var active: Boolean = true,

    // File 类型
    var image: FBFile? = null,

    // Array 类型：直接用 List/MutableList，元素可以是任意基础类型
    var tags: MutableList<String> = mutableListOf(),

    // Object 类型：直接用 data class
    var location: Location? = null,

    // Pointer 多对一
    @Pointer
    var author: AppUser? = null,

    // Relation 多对多
    @Relation
    var likes: MutableList<AppUser> = mutableListOf(),
) : FBObject("Post")
