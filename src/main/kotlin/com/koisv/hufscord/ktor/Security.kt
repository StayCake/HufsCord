package com.koisv.hufscord.ktor

import com.koisv.hufscord.Events.verifyThreadClose
import com.koisv.hufscord.data.GoogleInfo
import com.koisv.hufscord.data.GoogleSession
import com.koisv.hufscord.data.LinkedUser
import com.koisv.hufscord.discordInit
import com.koisv.hufscord.instance
import com.koisv.hufscord.instanceBot
import com.koisv.hufscord.ktor.KtorClient.httpClient
import com.koisv.hufscord.memberList
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.createTextChannel
import dev.kord.core.behavior.getChannelOf
import dev.kord.core.entity.Member
import dev.kord.core.entity.channel.TextChannel
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlin.collections.set

fun Application.configureSecurity() {
    val redirects = mutableMapOf<String, String>()
    authentication {
        oauth("auth-oauth-google") {
            urlProvider = { "https://hca.koisv.com/finish" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                    accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
                    requestMethod = HttpMethod.Post,
                    clientId = instanceBot.oAuthID,
                    clientSecret = instanceBot.oAuthKey,
                    defaultScopes = listOf("https://www.googleapis.com/auth/userinfo.profile"),
                    extraAuthParameters = listOf("access_type" to "offline"),
                    onStateCreated = { call, state ->
                        redirects[state] = call.request.queryParameters["redirectUrl"]!!
                    }
                )
            }
            client = HttpClient(Apache)
        }
    }
    routing {
        authenticate("auth-oauth-google") {
            get("/login") {
                // Redirects to 'authorizeUrl' automatically
            }

            get("/finish") {
                val principal: OAuthAccessTokenResponse.OAuth2? = call.principal()
                call.sessions.set(GoogleSession(principal!!.state!!, principal.accessToken))
                val redirect = redirects[principal.state!!]
                call.respondRedirect(redirect!!)
            }
        }
        get("/link") {
            if (!discordInit())
                return@get call.respondText("디스코드 봇이 작동 중이지 않습니다.\n관리자에게 연락바랍니다.")

            val userSession: GoogleSession? = call.sessions.get()
            val dataCheck = if (userSession != null)
                httpClient.get("https://www.googleapis.com/oauth2/v2/userinfo") {
                    headers { append(HttpHeaders.Authorization, "Bearer ${userSession.token}") }
                } else null

            if (userSession != null && dataCheck?.status == HttpStatusCode.OK) {
                val googleInfo: GoogleInfo = dataCheck.body()
                val data = googleInfo.finalize(
                    call.parameters["uid"] ?:
                    return@get call.respond("디스코드 아이디가 누락되었습니다!\n다시 시도해 주세요.")
                )
                if (memberList.none { it.linkedDSU == data.linkedDSU || it.googleID == data.googleID }) {
                    val findMember = instance.guilds.first().members.filter {it.asUser().id == data.linkedDSU}.firstOrNull()
                    if (findMember != null) {
                        memberList.add(data)
                        findMember.addRole(
                            Snowflake(1111677272693420052),
                            "인증 완료 자동 지급 | 외대생"
                        )
                        findMember.campusFind(data.major)
                        verifyThreadClose(
                            findMember.guild.getChannelOf<TextChannel>(Snowflake(1111638858686283827)),
                            data.linkedDSU
                        )
                        call.respondText("""<html><body><script>
                            |alert("인증이 완료되었습니다!")
                            |window.close()
                            |</script></body></html>""".trimMargin(),
                            ContentType.Text.Html, HttpStatusCode.OK)
                    } else call.respondText("서버에 참여 후 인증하셔야 합니다!")
                } else if (memberList.any { it.linkedDSU == data.linkedDSU && it.googleID == data.googleID }) {
                    val findMember = instance.guilds.first().members.filter {it.asUser().id == data.linkedDSU}.firstOrNull()
                    if (findMember != null) {
                        findMember.addRole(
                            Snowflake(1111677272693420052),
                            "인증 복원 자동 지급 | 외대생"
                        )
                        findMember.campusFind(data.major)
                        verifyThreadClose(
                            findMember.guild.getChannelOf<TextChannel>(Snowflake(1111638858686283827)),
                            data.linkedDSU
                        )
                        call.respondText("""<html><body><script>
                            |alert("이전 인증 이력이 복원되었습니다!\n이제 창을 닫으셔도 됩니다.")
                            |window.close()
                            |</script></body></html>""".trimMargin(),
                        ContentType.Text.Html, HttpStatusCode.OK)
                        //call.respondText("이전 인증 이력이 복원되었습니다! 이제 창을 닫으셔도 됩니다.")
                    } else call.respondText("서버에 참여 후 인증하셔야 합니다!")
                } else call.respondText("인증 이력이 이미 존재합니다! 관리자에게 연락바랍니다.")
            } else {
                val redirectUrl = URLBuilder("https://hca.koisv.com/login").run {
                    parameters.append("redirectUrl", call.request.uri)
                    build()
                }
                call.respondRedirect(redirectUrl)
            }
        }
    }
}

fun GoogleInfo.finalize(s: String): LinkedUser {
    val name = givenName.split("[")[0]
    val status: LinkedUser.Type =
        when (givenName.split("[")[1].split(" ")[0]) {
            "재학" -> LinkedUser.Type.Attend
            "졸업" -> LinkedUser.Type.Graduate
            else -> LinkedUser.Type.Other
        }
    val major = givenName.split(" ")[2]
        .removeSuffix("]")
        .replace(Regex("/[.]/gm"), "-")

    return LinkedUser(status, name, major, id, Snowflake(s.toLong()))
}

suspend fun Member.campusFind(major: String) {
    val campusList = httpClient.get {
        url.set {
            protocol = URLProtocol.HTTPS
            host = "hufs.ac.kr"
            path("user/indexSub.action")
        }
        parameters {
            parameter("codyMenuSeq", "37023")
            parameter("siteId", "hufs")
        }
    }.bodyAsText()
        .split("sm_wrap03")[1]
        .split("sm_wrap04")[0]
        .replace(Regex("/[ㆍ·]/gm"), "-")
    val academy = campusList.split("<h3 class=\"sm_link0303\">")[1]
    val global = campusList.split("<h3 class=\"sm_link0302\">")[1]
        .split("<h3 class=\"sm_link0303\">")[0]
    val seoul = campusList.split("<h3 class=\"sm_link0301\">")[1]
        .split("<h3 class=\"sm_link0302\">")[0]

    when (major) {
        in academy -> {
            addRole(
                Snowflake(1111952029707943956),
                "인증 완료 자동 지급 | 대학원"
            )
            if (guild.channels.firstOrNull { it.name == major } == null)
                guild.createTextChannel(major) {
                    reason = "누락 채널 신설"
                    parentId = Snowflake(1111951946086101083)
                }
        }
        in seoul -> {
            addRole(
                Snowflake(1111690080768229457),
                "인증 완료 자동 지급 | 캠퍼스"
            )
            if (guild.channels.firstOrNull { it.name == major } == null)
                guild.createTextChannel(major) {
                    reason = "누락 채널 신설"
                    parentId = Snowflake(1111639576767893555)
                }
        }
        in global -> {
            addRole(
                Snowflake(1111690138150522880),
                "인증 완료 자동 지급 | 캠퍼스"
            )
            if (guild.channels.firstOrNull { it.name == major } == null)
                guild.createTextChannel(major) {
                    reason = "누락 채널 신설"
                    parentId = Snowflake(1111639540235505705)
                }
        }
    }
}

