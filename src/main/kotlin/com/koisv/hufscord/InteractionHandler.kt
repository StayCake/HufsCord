package com.koisv.hufscord

import com.koisv.hufscord.data.CafeteriaCode
import com.koisv.hufscord.data.DataManager
import com.koisv.hufscord.data.DayMeal
import com.koisv.hufscord.data.Meal
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
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kord.rest.builder.component.option
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.actionRow
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.delay
import kotlinx.datetime.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

object InteractionHandler {
    suspend fun regCmd() {
        instance.createGuildChatInputCommand(
            Snowflake(1111607223479697479), "vms", "VerifyMessage"
        )
        instance.createGuildChatInputCommand(
            Snowflake(1111607223479697479), "off", "Shutdown"
        )
        instance.createGuildChatInputCommand(
            Snowflake(1111607223479697479),
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
            Snowflake(1111607223479697479),
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

    private suspend fun sendMeal(e: GuildChatInputCommandInteractionCreateEvent) {
        val mealCode = e.interaction.command.strings["위치"]
        val codeFind = CafeteriaCode.values().find { it.strCode == mealCode }
        if (mealCode != null && codeFind != null) {
            mealCache[mealCode]?.let {
                val day = LocalDate.now().toKotlinLocalDate()
                val data = it[day]
                if (data != null) {
                    e.interaction.channel.createMessage {
                        actionRow { buildSelect(mealCode, day) }
                        embed { data.meals[0]?.let { meal -> buildForm(meal, codeFind) } }
                        actionRow { buildNavi(data, mealCode, day) }
                    }
                }
            }
        }
    }

    private fun ActionRowBuilder.buildSelect(myCode: String, day: kotlinx.datetime.LocalDate) {
        val dateFormat = DateTimeFormatter.ofPattern("yyMMdd")
        stringSelect("mealSelect") {
            for (i in mealCache.keys) {
                if (myCode != i.lowercase()) {
                    val code = CafeteriaCode.values().find { c -> c.strCode == i }
                    code?.vName?.let { name ->
                        option(name,
                            "meal${
                                if (code.intCode > 200) "G" else "S"
                            }-${code.strCode}-${
                                day.toJavaLocalDate().format(dateFormat)
                            }-0")
                    }
                }
            }
        }
    }

    private suspend fun EmbedBuilder.buildForm(meal: Meal, mealCode: CafeteriaCode) {
        author {
            name = "${if (mealCode.intCode > 200) "[글로벌]" else "[서울]"} ${mealCode.vName}"
            icon = instance.getSelf().avatar?.cdnUrl?.toUrl()
        }
        title = "${meal.name} | ${meal.price}원"
        if (meal.kcal != 0) title += " | ${meal.kcal} kcal"
        val mealTime = meal.time
        val timeFormat = DateTimeFormatter.ofPattern("hh:mm")
        footer {
            text = "${
                mealTime.first.toJavaLocalTime().format(timeFormat)
            } ~ ${
                mealTime.second.toJavaLocalTime().format(timeFormat)
            }"
        }
        field {
            name = if (meal.isSpecial) "특별식" else "일반식"
            value = meal.menus.joinToString("\n")
        }
        timestamp = Clock.System.now()
    }

    private fun ActionRowBuilder.buildNavi(
        meals: DayMeal,
        myCode: String,
        day: kotlinx.datetime.LocalDate,
        index: Int = 0
    ) {
        val codeFind = CafeteriaCode.values().find { it.strCode == myCode }
        val dateFormat = DateTimeFormatter.ofPattern("yyMMdd")
        interactionButton(
            if (meals.isFirstDay) ButtonStyle.Danger else ButtonStyle.Primary,
            "meal${if (codeFind?.intCode!! > 200) "G" else "S"}-$myCode-${
            day.minus(DatePeriod(days = 1)).toJavaLocalDate()
                .format(dateFormat)
        }-${index-1}") {
            emoji = DiscordPartialEmoji(null , "◀")
            label = "어제"
            disabled = meals.isFirstDay
        }
        for (i in meals.meals.indices) {
            interactionButton(ButtonStyle.Primary,
                "meal${if (codeFind.intCode > 200) "G" else "S"}-$myCode-${
                day.toJavaLocalDate().format(dateFormat)
            }-$i") {
                emoji = DiscordPartialEmoji(null , "▶")
                label = meals.meals[index]?.name
                disabled = i == 0
            }
        }
        interactionButton(
            if (meals.isLastDay) ButtonStyle.Danger else ButtonStyle.Primary,
            "mealG-$myCode-${
            day.plus(DatePeriod(days = 1)).toJavaLocalDate()
                .format(dateFormat)
        }-${index+1}") {
            emoji = DiscordPartialEmoji(null , "▶")
            label = "내일"
            disabled = meals.isLastDay
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