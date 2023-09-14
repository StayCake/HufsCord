package com.koisv.hufscord.func

import com.koisv.hufscord.Schedules.Companion.myFilter
import com.koisv.hufscord.data.SitePost
import com.koisv.hufscord.instance
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.getChannelOf
import dev.kord.core.entity.channel.NewsChannel
import dev.kord.rest.builder.message.create.actionRow
import dev.kord.rest.builder.message.create.embed
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter

object Notice {

    fun MutableList<String>.finalize(): List<SitePost> {
        val newList = mutableListOf<SitePost>()
        forEach { data ->
            val oRegex = Regex("/\\[[가-힣]+\\]/")
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

                newList.add(
                    SitePost(number, code,
                    type.ifBlank { if (title matches oRegex) oRegex.find(title)!!.value else "[공통]" }, title, poster, date)
                )
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
                newList.add(
                    SitePost(number, code,
                    type.ifBlank { if (title matches oRegex) oRegex.find(title)!!.value else "[공통]" }, title, poster, date)
                )
            }
        }
        return newList
    }

    suspend fun SitePost.send(postNum: Int, channel: Long) {
        val channelId = Snowflake(channel)
        instance.guilds.first().getChannelOf<NewsChannel>(channelId)
            .createMessage {
                embed {
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
                actionRow {
                    linkButton(
                        url {
                            protocol = URLProtocol.HTTPS
                            host = "hufs.ac.kr"
                            path("user/indexSub.action")

                            parameters {
                                append("codyMenuSeq", when (postNum) {
                                        0 -> "37079"
                                        1 -> "37080"
                                        2 -> "37081"
                                        3 -> "37082"
                                        else -> "37078" }
                                )
                                append("siteId", "hufs")
                                append("command", "view")
                                append("sortChar", "AB")
                                append("boardSeq", this@send.code.toString())
                            }
                        }
                    ) { this.label = "바로가기" }
                }
            }
    }
}