package com.koisv.hufscord

import com.koisv.hufscord.Schedules.Companion.getNotice
import dev.kord.common.entity.ArchiveDuration
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.DiscordPartialEmoji
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.getChannelOf
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.Event
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.guild.MemberJoinEvent
import dev.kord.core.event.guild.MemberLeaveEvent
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.kordLogger
import dev.kord.rest.builder.message.create.actionRow
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.minutes

object Events {
    private suspend fun verifyThreadCreate(channel: TextChannel, id: Snowflake) {
        val thread = channel.activeThreads.firstOrNull { it.name == "인증-$id" }
            ?: channel.getPrivateArchivedThreads().firstOrNull { it.name == "인증-$id" }
            ?: channel.startPrivateThread("인증-$id") {
                autoArchiveDuration = ArchiveDuration.Hour
                reason = "인증 절차 | $id"

            }
        thread.createMessage("""<@$id>님 환영합니다.
                    |서버 이용을 위해 다음과 같은 인증 절차가 필요합니다.
                    |인증에는 HUFS 메일 외 다른 계정으로 할 수 없습니다. 
                    |https://hca.koisv.com/link?uid=$id
                """.trimMargin())
    }
    suspend fun verifyThreadClose(channel: TextChannel, id: Snowflake) {
        channel.activeThreads.firstOrNull { it.name == "인증-$id" }
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
                instance.createGuildChatInputCommand(
                    Snowflake(1111607223479697479), "vms", "VerifyMessage"
                )
                instance.createGuildChatInputCommand(
                    Snowflake(1111607223479697479), "off", "Shutdown"
                )
                coroutineScope {
                    withContext(Dispatchers.IO) {
                        launch {
                            getNotice(0)
                            delay(1.minutes)
                            getNotice(1)
                            delay(1.minutes)
                            getNotice(2)
                            delay(1.minutes)
                            getNotice(3)
                        }
                    }
                }
            }
            is ButtonInteractionCreateEvent -> {
                val button = e.interaction
                if (button.componentId == "selfVerify")
                    verifyThreadCreate(
                        e.interaction.channel.asChannelOf<TextChannel>(),
                        e.interaction.user.id
                    )
                val respond = button.respondEphemeral { content = "요청 전송 완료!" }
                delay(3000)
                respond.delete()
            }
            is GuildChatInputCommandInteractionCreateEvent -> {
                when (e.interaction.command.rootName) {
                    "vms" -> {
                        e.interaction.respondEphemeral { content = "Done" }
                        e.interaction.channel.createMessage {
                            embed {
                                author {
                                    name = "Welcome To HUFSCord"
                                    icon = instance.getSelf().avatar?.cdnUrl?.toUrl()
                                }
                                title = "한국외대 비공식 디스코드에 오신 것을 환영합니다!"
                                description = """쾌적한 서버 이용을 위해 본인인증 절차를 거치고 있습니다.
                                            |자동으로 생성된 스레드를 확인하시기 바라며, 없을 경우 아래 버튼을 눌러주세요.
                                        """.trimMargin()
                                timestamp = Clock.System.now()
                            }
                            actionRow {
                                interactionButton(
                                    ButtonStyle.Primary,
                                    "selfVerify") {
                                    emoji = DiscordPartialEmoji(null , "🆔")
                                    label = "인증하기"
                                }
                            }
                        }
                    }
                    "off" -> {
                        e.interaction.respondEphemeral { content = "Shutting Down." }
                        logger.info { "[!] Shutdown - ${e.interaction.user.tag}" }
                        DataManager.save()
                        autoSave.cancel()
                        webHost.stop()
                        instance.logout()
                        exitProcess(0)
                    }
                }
            }
        }
    }
}
