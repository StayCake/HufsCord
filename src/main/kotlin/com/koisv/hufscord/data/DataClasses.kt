package com.koisv.hufscord.data

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class GoogleInfo(
    val id: String,
    val locale: String,
    @JsonProperty("given_name") val givenName: String,
    /*@JsonProperty("family_name") val familyName: String,
    val link: String, < 가끔 누락 있어서 불필요 인수 전체 제거
    val name: String,
    val picture: String,*/
)

@Serializable
data class DayMeal(
   val meals: List<Meal?>,
   val isFirstDay: Boolean,
   val isLastDay: Boolean,
)

@Serializable
data class Meal(
    var time: Pair<LocalTime, LocalTime>,
    var menus: List<String>,
    var name: String,
    var isSpecial: Boolean = false,
    var kcal: Int = 0,
    var price: Int = 0,
)

@Serializable
data class LastNum(
    var n1Post: Int = 0,
    var n2Post: Int = 0,
    var n3Post: Int = 0,
    var n4Post: Int = 0
)

data class SitePost(
    val number: Int,
    val code: Int,
    val type: String,
    val title: String,
    val poster: String,
    val date: LocalDate
)

data class GoogleSession(val state: String, val token: String)

enum class CafeteriaCode {
    Psb("인문관", "psb", 101),
    Pfb("교수회관", "pfb", 102),
    Stu("학생식당", "stu", 203),
    Pfs("교직원식당", "pfs", 202),
    Dmt("식당", "dmt", 205);

    lateinit var vName: String
    lateinit var strCode: String
    var intCode: Int = 0

    constructor()

    constructor(vName: String, strCode: String, intCode: Int) {
        this.strCode = strCode
        this.intCode = intCode
        this.vName = vName
    }
}