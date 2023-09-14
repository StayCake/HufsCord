package com.koisv.hufscord

import com.koisv.hufscord.data.DataManager
import com.koisv.hufscord.data.tGid
import com.koisv.hufscord.func.Meals.sendMeal
import com.koisv.hufscord.ktor.KtorClient.httpClient
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.DiscordPartialEmoji
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.create.actionRow
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.system.exitProcess

object InteractionHandler {
    suspend fun regCmd() {
        instance.createGuildChatInputCommand(
            if (!instanceBot.isTest)
                Snowflake(1111607223479697479) else tGid,
            "vms", "VerifyMessage"
        )
        instance.createGuildChatInputCommand(
            if (!instanceBot.isTest)
                Snowflake(1111607223479697479) else tGid,
            "off", "Shutdown"
        )
        instance.createGuildChatInputCommand(
            if (!instanceBot.isTest)
                Snowflake(1111607223479697479) else tGid,
            "설캠밥",
            "아 학식 알려주세요 현기증 난단 말이에요"
        ) {
            string("위치", "어디 밥 줄까") {
                choice("인문관", "psb") {}
                choice("교수회관", "pfb") {}
                required = true
            }
        }
        instance.createGuildChatInputCommand(
            if (!instanceBot.isTest)
                Snowflake(1111607223479697479) else tGid,
            "글캠밥",
            "아 학식 알려주세요 현기증 난단 말이에요"
        ) {
            string("위치", "어디 밥 줄까") {
                choice("학생식당", "stu") {}
                choice("교직식당", "pfs") {}
                choice("긱사식당", "dmt") {}
                required = true
            }
        }
    }

    suspend fun eventHandle(e: ChatInputCommandInteractionCreateEvent) {
        when (e) {
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
                        httpClient.close()
                        DataManager.save()
                        autoSave.cancel()
                        webHost.stop()
                        instance.logout()
                        exitProcess(0)
                    }
                    "설캠밥" -> { sendMeal(e) }
                    "글캠밥" -> { sendMeal(e) }
                }
            }
            else -> {}
        }
    }

    suspend fun eventHandle(e: ButtonInteractionCreateEvent) {
        val button = e.interaction
        val ident = button.componentId.split("-")
        when (ident[0]) {
            "selfVerify" -> {
                Events.verifyThreadCreate(
                    e.interaction.channel.asChannelOf<TextChannel>(),
                    e.interaction.user.id
                )
                val respond = button.respondEphemeral { content = "요청 전송 완료!" }
                delay(3000)
                respond.delete()
            }

        }
    }
}