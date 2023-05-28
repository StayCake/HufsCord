package com.koisv.hufscord

import com.koisv.hufscord.Schedules.Companion.myFilter
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
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.hours

class Schedules {
    companion object {
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

data class SitePost(
    val number: Int,
    val code: Int,
    val type: String,
    val title: String,
    val poster: String,
    val date: LocalDate
)