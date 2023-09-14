package com.koisv.hufscord.func

import com.koisv.hufscord.Schedules
import com.koisv.hufscord.data.CafeteriaCode
import com.koisv.hufscord.data.DayMeal
import com.koisv.hufscord.data.Meal
import com.koisv.hufscord.instance
import com.koisv.hufscord.mealCache
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.DiscordPartialEmoji
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kord.rest.builder.component.option
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.actionRow
import dev.kord.rest.builder.message.create.embed
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.datetime.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*
import java.util.function.Supplier
import kotlinx.datetime.LocalDate as KLocalDate
import java.time.LocalTime as JLocalTime

object Meals {
    suspend fun sendMeal(e: GuildChatInputCommandInteractionCreateEvent) {
        val mealCode = e.interaction.command.strings["위치"]
        val codeFind = CafeteriaCode.values().find { it.strCode == mealCode }
        if (mealCode != null && codeFind != null) {
            mealCache[mealCode]?.let {
                val day = LocalDate.now().toKotlinLocalDate()
                val data = it[day]
                if (data != null) {
                    e.interaction.channel.createMessage {
                        //actionRow { mealNavi(data, mealCode, day) }
                        embed { data.meals[0]?.let { meal -> mealForm(meal, codeFind) } }
                        actionRow { mealSelect(mealCode, day) }
                    }
                }
            }
        }
    }

    private suspend fun EmbedBuilder.mealForm(meal: Meal, mealCode: CafeteriaCode) {
        author {
            name = "${if (mealCode.intCode > 200) "[글로벌]" else "[서울]"} ${mealCode.vName}"
            icon = instance.getSelf().avatar?.cdnUrl?.toUrl()
        }
        title = "${meal.name} | ${meal.price}원"
        if (meal.kcal != 0) title += " | ${meal.kcal} kcal"
        val mealTime = meal.time
        val timeFormat = DateTimeFormatter.ofPattern("hh:mm")
        footer {
            text = buildString {
                append(mealTime.first.toJavaLocalTime().format(timeFormat))
                append(" ~ ")
                append(mealTime.second.toJavaLocalTime().format(timeFormat))
            }
        }
        field {
            name = if (meal.isSpecial) "특별식" else if (meal.menus.isNotEmpty()) "일반식" else "<메뉴 없음>"
            value = if (meal.menus.isNotEmpty()) meal.menus.joinToString("\n")
            else "오늘은 메뉴가 없어요~"
        }
        timestamp = Clock.System.now()
    }

    private fun ActionRowBuilder.mealNavi(
        meals: DayMeal,
        myCode: String,
        day: KLocalDate,
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
            interactionButton(
                ButtonStyle.Primary,
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

    private fun ActionRowBuilder.mealSelect(myCode: String, day: KLocalDate) {
        val dateFormat = DateTimeFormatter.ofPattern("yyMMdd")
        stringSelect("mealSelect") {
            for (i in mealCache.keys) {
                if (myCode != i.lowercase()) {
                    val code = CafeteriaCode.values().find { c -> c.strCode == i }
                    println(code)
                    code?.vName?.let { name ->
                        option(
                            name,
                            buildString {
                                append("meal")
                                append(if (code.intCode > 200) "G" else "S")
                                append("-")
                                append(code.strCode)
                                append("-")
                                append(day.toJavaLocalDate().format(dateFormat))
                                append("-0")
                            }
                        )
                    }
                }
            }
        }
    }


    fun requestFood(id: Int): MutableMap<KLocalDate, DayMeal> {
        val firstDay = Schedules.date.date.toJavaLocalDate()
            .with(WeekFields.of(Locale.KOREA).dayOfWeek(), 2L)
        val newJsoup = Jsoup.connect(
            url {
                protocol = URLProtocol.HTTPS
                host = "wis.hufs.ac.kr"
                path("jsp/HUFS/cafeteria/viewWeek.jsp")
                parameters {
                    append("startDt", firstDay.minusDays(7)
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    )
                    append("endDt", firstDay.plusDays(7)
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd")))
                    append("caf_id", "h$id")
                }
            }
        ).get()

        val jsTable: Element = newJsoup.select("table")[0] // 메인 식단표 수집
        val jsRow: Elements = jsTable.select("tr")

        val timeLabelFormatter = DateTimeFormatter
            .ofPattern("HHmm")
            .withLocale(Locale.KOREAN)
            .withZone(ZoneId.systemDefault())

        val menuRow = jsRow.subList(1, jsRow.size).filter { mainElement ->
            mainElement.select("td").any { it.select("table").size > 0 }
        }

        val tempTable = mutableListOf<List<String>>()
        val nameTableRaw = mutableListOf<String>()
        val nameTable = mutableMapOf<String, List<LocalTime>>()

        // 메뉴 - Col : 한 줄 | table : 한 칸 | tr : 한 개
        menuRow.forEach { table ->
            val menuCol = table.select("td").toMutableList()

            val nameLabel = menuCol.removeFirst()
            nameTableRaw += nameLabel.text()

            for (menus in menuCol)
                for (row in menus.select("table"))
                    tempTable += row.select("tr")
                        .filter { e -> e.text().isNotBlank() }
                        .map(Element::text)
        }

        // 식단명, 시간 정리
        nameTableRaw
            .map { it.split(" ") } // <br>로 좌우 분리 - 식단명 | 시간
            .forEach { labelList ->
                nameTable[labelList[0]] = labelList[1]
                    .split("~") // 시작 시간 ~ 마감 시간
                    .map { JLocalTime.parse(it, timeLabelFormatter).toKotlinLocalTime() }
            }
        
        val mainCache = mutableMapOf<KLocalDate, DayMeal>() // 캐시 초기화
        val durationSupplier = Supplier { firstDay.datesUntil(firstDay.plusDays(14)) } // Stream 재사용
        val meals = mutableListOf<Meal?>() // 공백 Meal 리스트

        for (i in 0 until durationSupplier.get().count().toInt())
            for ((menuName, menuTime) in nameTable) // 순서 변환 [식사간 -> 일간]
                meals.add(
                    tempTable[i + (15 * nameTable.keys.indexOf(menuName))]
                        .toMeal(menuName, menuTime) // 메뉴 변환
                )

        for ((idx, mealList) in meals.chunked(nameTable.keys.size).withIndex()) {
            val date = durationSupplier.get().toList()[idx]
            mainCache[date.toKotlinLocalDate()] =
                DayMeal(mealList, idx == 0, idx == (durationSupplier.get().count() - 1).toInt())
        }
        
        return mainCache
    }

    private fun List<String>.toMeal(menuName: String, menuTime: List<LocalTime>): Meal? {
        require(menuTime.size == 2)
        val price = takeLast(1).toString()
            .filter { it.isDigit() }
            .toIntOrNull()
        if (size <= 1 || price == null) return null

        val kcal = this[size - 2]
            .filter { it.isDigit() }
            .toIntOrNull()
        val isSpecial = this.first().contains("특식")

        return Meal(
            Pair(menuTime[0], menuTime[1]),
            this.subList(if (isSpecial) 1 else 0, this.size - if (kcal == null) 1 else 2),
            menuName, isSpecial, kcal ?: 0, price
        )
    }
}