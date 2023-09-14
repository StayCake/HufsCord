package com.koisv.hufscord

import com.koisv.hufscord.Schedules.Companion.getFood
import com.koisv.hufscord.Schedules.Companion.getNotice
import dev.kord.common.entity.ArchiveDuration
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.getChannelOf
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.Event
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.guild.MemberJoinEvent
import dev.kord.core.event.guild.MemberLeaveEvent
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.InteractionCreateEvent
import dev.kord.core.kordLogger
import dev.kord.rest.builder.message.create.actionRow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes

object Events {
    suspend fun verifyThreadCreate(channel: TextChannel, id: Snowflake) {
        val thread = channel.activeThreads.firstOrNull { it.name == "인증-$id" }
            ?: channel.getPrivateArchivedThreads().firstOrNull { it.name == "인증-$id" }
            ?: channel.startPrivateThread("인증-$id") {
                autoArchiveDuration = ArchiveDuration.Hour
                reason = "인증 절차 | $id"

            }
        thread.createMessage {
            content = """<@$id>님 환영합니다.
                    |서버 이용을 위해 다음과 같은 인증 절차가 필요합니다.
                    |인증에는 HUFS 메일 외 다른 계정으로 할 수 없습니다. 
                """.trimMargin()
            actionRow {
                linkButton("https://hca.koisv.com/link?uid=$id") { label = "바로가기" }
            }
        }
    }
    suspend fun verifyThreadClose(channel: TextChannel, id: Snowflake) {
        channel.activeThreads.firstOrNull { it.name.endsWith(id.toString()) }
            ?.delete("인증 완료 | $id")
    }

    suspend fun handle(e: Event) {
        when (e) {
            is MemberJoinEvent -> {
                verifyThreadCreate(
                    e.guild.getChannelOf<TextChannel>(Snowflake(1111638858686283827)),
                    e.member.asUser().id
                )
            }
            is MemberLeaveEvent -> {
                memberList.find { it.linkedDSU == e.user.id }
                    ?.let { println("[-] ${it.linkedDSU} | ${it.googleID}") }
                memberList.removeAll { it.linkedDSU == e.user.id }
                verifyThreadClose(
                    e.guild.getChannelOf<TextChannel>(Snowflake(1111638858686283827)),
                    e.user.id
                )
            }
            is ReadyEvent -> {
                kordLogger.info(
                    "Logged On ${instance.getSelf().tag} | ${instance.guilds.first().name} DSV Detected"
                )
                Uptime = Clock.System.now()
                instance.editPresence {
                    watching(instanceBot.presence)
                    since = Clock.System.now()
                }
                InteractionHandler.regCmd()
                if (!instanceBot.isTest) coroutineScope {
                    withContext(Dispatchers.IO) {
                        launch {
                            getFood()
                            getNotice(0)
                            delay(1.minutes)
                            getNotice(1)
                            delay(1.minutes)
                            getNotice(2)
                            delay(1.minutes)
                            getNotice(3)
                            delay(1.minutes)
                        }
                    }
                }
            }
            is InteractionCreateEvent -> when (e) {
                is ButtonInteractionCreateEvent -> InteractionHandler.eventHandle(e)
                is ChatInputCommandInteractionCreateEvent -> InteractionHandler.eventHandle(e)
                else -> {}
            }
        }
    }
}
