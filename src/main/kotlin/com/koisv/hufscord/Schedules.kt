package com.koisv.hufscord

import com.koisv.hufscord.data.CafeteriaCode
import com.koisv.hufscord.func.Meals.requestFood
import com.koisv.hufscord.func.Notice.finalize
import com.koisv.hufscord.func.Notice.send
import com.koisv.hufscord.ktor.KtorClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class Schedules {
    companion object {
        val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        fun String.myFilter(): String = this.filterNot { it == '\t' || it == '\n' }.trim()

        suspend fun getNotice(type: Int = 0) : Job =
            CoroutineScope(Dispatchers.IO).launch {
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
                    /*val finPost = mutableListOf<SitePost>()
                    val allPosts = noticeData.finalize()
                        .sortedByDescending { it.number }
                    val (lpNumber) = allPosts.first()
                    when (type) {
                        1 -> if (lastNums.n1Post < lpNumber) {
                            finPost.addAll(allPosts.take(lpNumber - lastNums.n1Post))
                            lastNums.n1Post = lpNumber
                        }
                        2 -> if (lastNums.n2Post < lpNumber) {
                            finPost.addAll(allPosts.take(lpNumber - lastNums.n2Post))
                            lastNums.n2Post = lpNumber
                        }
                        3 -> if (lastNums.n3Post < lpNumber) {
                            finPost.addAll(allPosts.take(lpNumber - lastNums.n3Post))
                            lastNums.n3Post = lpNumber
                        }
                        else -> if (lastNums.n4Post < lpNumber) {
                            finPost.addAll(allPosts.take(lpNumber - lastNums.n4Post))
                            lastNums.n4Post = lpNumber
                        }
                    }
                    finPost.forEach { it.send(type, 1111639241861115965) }*/
                    delay(2.hours)
                }
            }

        suspend fun getFood(): Job =
            CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    CafeteriaCode.values().forEach { cc ->
                        if (
                            mealCache.values.any { meals ->
                                meals.any { it.key.daysUntil(date.date) >= 13 }
                            } || !mealCache.containsKey(cc.strCode)
                        ) mealCache[cc.strCode] = requestFood(cc.intCode)
                        delay(1.minutes)
                    }
                    delay(0.5.days)
                }
            }
    }
}



