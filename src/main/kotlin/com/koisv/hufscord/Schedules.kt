package com.koisv.hufscord

import com.koisv.hufscord.Schedules.Companion.myFilter
import com.koisv.hufscord.data.CafeteriaCode
import com.koisv.hufscord.data.DayMeal
import com.koisv.hufscord.data.Meal
import com.koisv.hufscord.data.SitePost
import com.koisv.hufscord.ktor.KtorClient
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.getChannelOf
import dev.kord.core.entity.channel.NewsChannel
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class Schedules {
    companion object {
        private val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        fun String.myFilter(): String {
            return this.filterNot { it == '\t' || it == '\n' }.trim()
        }

        suspend fun getNotice(type: Int = 0) : Job {
            return CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    val noticeData = KtorClient.httpClient.get {
                        url.set {
                            protocol = URLProtocol.HTTPS
                            host = "hufs.ac.kr"
                            path("user/indexSub.action")
                        }
                        parameters {
                            parameter("codyMenuSeq", when (type) {
                                1 -> 37080
                                2 -> 37081
                                3 -> 37082
                                else -> 37078
                            }.toString())
                            parameter("siteId", "hufs")
                        }
                    }.bodyAsText()
                        .split("<tr>").toMutableList()
                    noticeData.removeAt(0)
                    val lastPost = noticeData.finalize().maxByOrNull { it.number }!!
                    when (type) {
                        1 -> {
                            if (lastNums.n1Post < lastPost.number) {
                                lastNums.n1Post = lastPost.number
                                lastPost.send(type, 1111639241861115965)
                            }
                        }
                        2 -> {
                            if (lastNums.n2Post < lastPost.number) {
                                lastNums.n2Post = lastPost.number
                                lastPost.send(type, 1111639241861115965)
                            }
                        }
                        3 -> {
                            if (lastNums.n3Post < lastPost.number) {
                                lastNums.n3Post = lastPost.number
                                lastPost.send(type, 1111639241861115965)
                            }
                        }
                        else -> {
                            if (lastNums.n4Post < lastPost.number) {
                                lastNums.n4Post = lastPost.number
                                lastPost.send(type, 1111639241861115965)
                            }
                        }
                    }
                    delay(3.hours)
                }
            }
        }

        private suspend fun SitePost.send(postNum: Int, channel: Long) {
            val channelId = Snowflake(channel)
            instance.guilds.first().getChannelOf<NewsChannel>(channelId)
                .createEmbed {
                    title = this@send.title
                    author {
                        name = "$type ${
                            when (postNum) {
                                0 -> "일반 공지"
                                1 -> "학사 공지"
                                2 -> "장학 공지"
                                3 -> "채용 공지"
                                else -> "오류"
                            }
                        }"
                        icon = instance.getSelf().avatar?.cdnUrl?.toUrl()
                    }

                    field {
                        inline = false
                        name = "바로가기"
                        value = "https://hufs.ac.kr/user/indexSub.action" +
                                "?codyMenuSeq=37078&siteId=hufs&command=view&boardSeq=${this@send.code}"
                    }
                    field {
                        inline = true
                        name = "작성처"
                        value = this@send.poster
                    }
                    field {
                        inline = true
                        name = "작성일"
                        value = this@send.date.toJavaLocalDate()
                            .format(DateTimeFormatter.ofPattern("yy/MM/dd"))
                    }

                    timestamp = Clock.System.now()
                }
        }

        suspend fun getFood(): Job {
            return CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    if (date.dayOfWeek.value == 1
                        && mealCache.values.any { meals -> meals.any { it.key.daysUntil(date.date) >= 13 } }) {
                        CafeteriaCode.values().forEach {
                            mealCache[it.strCode] = requestFood(it.intCode)
                            delay(1.minutes)
                        }
                    }
                }
            }
        }

        private suspend fun requestFood(id: Int): MutableMap<LocalDate, DayMeal> {
            val reqData = KtorClient.httpClient.get {
                val firstDay = date.date.toJavaLocalDate()
                    .with(WeekFields.of(Locale.KOREA).dayOfWeek(), 2L)
                url.set {
                    protocol = URLProtocol.HTTPS
                    host = "wis.hufs.ac.kr"
                    path("jsp/HUFS/cafeteria/viewWeek.jsp")
                }
                parameters {
                    parameter("startDt", firstDay.minusDays(7)
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    )
                    parameter("endDt", firstDay.plusDays(7)
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd")))
                    parameter("caf_id", "h$id")
                }
            }.bodyAsText().lowercase()

            val rows = reqData.split("<tr height='35'>").toMutableList()
            rows.removeFirst()
            val dates = rows.removeFirst().dateArray()
            val data = rows.map { it.tableData() }

            val menuTable = mutableListOf<Array<Any?>>()
            data.forEach { work ->
                val type = work.toMutableList()
                val typeLabel = type.removeFirst()[0]
                val name = typeLabel
                    .split("!")[0]
                val times = typeLabel
                    .split("!")[1]
                    .getTimes()
                val table = arrayOfNulls<Any>(15)
                table[0] = times
                type.forEachIndexed { index, dayMenu ->
                    val meal = dayMenu.menuFin(name, times)
                    table[index + 1] = meal
                }
                menuTable.add(table)
            }
            val mainCache = mealCache[id.toString()] ?: mutableMapOf()
            mainCache.clear()
            for (column in 1 until menuTable[0].size) {
                val date = dates[column - 1]
                if (date != null) mainCache[date] = DayMeal(
                    lazy {
                        val fullList = arrayOfNulls<Meal>(menuTable.size)
                        for (row in 0 until menuTable.size) {
                            fullList[row] = menuTable[row][column] as Meal?
                        }
                        return@lazy fullList
                    }.value.toList(), column == 1,
                    column == menuTable[0].size - 1
                )
            }
            return mainCache
        }

        private fun List<String>.menuFin(name: String, times: List<LocalTime>): Meal {
            val dataRaw = this.toMutableList()
            val rawPrice = dataRaw.removeLast()
            if (!rawPrice.endsWith("원"))
                return Meal(Pair(times[0], times[1]), listOf(), "NONE")
            val price = rawPrice.filter { it.isDigit() }.toInt()
            val kcal = if (dataRaw.last().endsWith("kcal"))
                dataRaw.removeLast().removeSuffix("kcal").toInt() else null
            return Meal(
                Pair(times[0], times[1]),
                dataRaw.filterNot { it == "[한정특식]" },
                name, dataRaw.first() == "[한정특식]",
                kcal ?: 0, price
            )
        }
        private fun String.tableData(): List<List<String>> {
            return split("liststyle")
                .asSequence()
                .map { it.replace("<br>", "!") }
                .map { it.replace("<한정특식>", "[한정특식]") }
                .map { it.split("</td>") }
                .map { it.map { d -> d.filterNot { c -> c.isWhitespace() } } }
                .map { it.map { d -> d.split(">")[ d.count { c -> '>' == c }] } }
                .map { it.filter { d -> d.isNotBlank() } }
                .map { it.filterNot { d -> d == "<tdalign='center'class='" }}
                .toList()
        }
        private fun String.dateArray(): Array<LocalDate?> {
            val format = DateTimeFormatter.ofPattern("yyyy/MM/dd(E)")
                .withLocale(Locale.KOREAN)
            val rawData = split("</td>")
                .asSequence()
                .map { it.replace("<br>", "!") }
                .map { it.filterNot { c -> c.isWhitespace() } }
                .map { it.split(">")[it.count { c -> '>' == c }] }
                .filter { it.isNotBlank() }
                .toMutableList()
            val array: Array<LocalDate?> = arrayOfNulls(14)
            rawData.removeFirst()
            rawData.map {
                java.time.LocalDate.parse("${java.time.LocalDate.now().year}/$it", format)
                    .toKotlinLocalDate()
            }.forEachIndexed { index, localDate ->
                array[index] = localDate
            }
            return array
        }
        private fun String.getTimes(): List<LocalTime> {
            val format = DateTimeFormatter.ofPattern("HHmm")
            val times = this.split("~")
            return listOf(
                java.time.LocalTime.parse(times[0], format).toKotlinLocalTime(),
                java.time.LocalTime.parse(times[1], format).toKotlinLocalTime()
            )
        }
    }
}

fun MutableList<String>.finalize(): List<SitePost> {
    val newList = mutableListOf<SitePost>()
    forEach { data ->
        if ("<span class=\"mini_eng\">" in data) {
            val spD = data.split("<span class=\"mini_eng\">").toMutableList()
            spD.removeAt(0)
            spD.removeLast()
            val number = spD[0].split("</span>")[0].filterNot { it.isWhitespace() }.toIntOrNull() ?: 0
            val type = spD[0].split("<td class=\"title\">")[1]
                .split("<a href=")[0].myFilter()

            val code = spD[0].split("&boardSeq=")[1].split("'>")[0].toIntOrNull() ?: 0
            val title = spD[0].split("'>")[1].split("&")[0].myFilter()
            val poster = spD[1].split("</span>")[0].myFilter()
            val date = LocalDate.parse(spD[2].split("</span>")[0].filterNot { it.isWhitespace() })
            newList.add(SitePost(number, code, if (type.isNotBlank()) type else "[공통]", title, poster, date))
        } else {
            val spD = data.split("<td class=\"no\">")[1]
            val number = spD.split("</td>")[0].filterNot { it.isWhitespace() }.toIntOrNull() ?: 0
            val type = "[공통]"
            val code = spD.split("&boardSeq=")[1].split("'>")[0].toIntOrNull() ?: 0
            val title = spD.split("'>")[1].split("</a>")[0].myFilter()
            val poster = spD.split("<td>")[1].split("</td>")[0].myFilter()
            val date = LocalDate.parse(
                spD.split("<td>")[2]
                    .split("</td>")[0]
                    .filterNot { it.isWhitespace() }
            )
            newList.add(SitePost(number, code, type, title, poster, date))
        }
    }
    return newList
}

