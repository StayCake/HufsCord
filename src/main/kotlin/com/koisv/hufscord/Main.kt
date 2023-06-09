package com.koisv.hufscord

import com.koisv.hufscord.data.*
import com.koisv.hufscord.data.DataManager.load
import com.koisv.hufscord.ktor.configureSecurity
import dev.kord.cache.map.MapLikeCollection
import dev.kord.cache.map.internal.MapEntryCache
import dev.kord.cache.map.lruLinkedHashMap
import dev.kord.core.Kord
import dev.kord.core.event.Event
import dev.kord.core.kordLogger
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.Intents
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.Job
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import mu.KLogger

lateinit var autoSave: Job
lateinit var instance: Kord
lateinit var instanceBot: Bot
lateinit var Uptime: Instant
lateinit var logger: KLogger
lateinit var lastNums: LastNum
lateinit var webHost: NettyApplicationEngine

fun discordInit() = ::instance.isInitialized
val memberList = mutableListOf<LinkedUser>()
val mealCache = mutableMapOf<String, MutableMap<LocalDate, DayMeal>>()

fun Application.module() {
    configureSecurity()
    install(Sessions) {
        cookie<GoogleSession>("LinkSession")
    }
}

suspend fun main(args: Array<String>) {
    memberList.load()
    mealCache.putAll(DataManager.cacheLoad())
    lastNums = load()
    val bots = DataManager.botLoad()

    instanceBot =
        if (args.isNotEmpty() && args[0].startsWith("bot"))
            if (args[0].split(":").size != 2)
                throw IndexOutOfBoundsException("올바른 숫자를 입력해 주세요. | 예) bot:1")
            else bots[args[0].split(":")[1].toInt()]
        else bots[0]
    instance = Kord(instanceBot.token) {
        cache {
            messages { cache, description ->
                MapEntryCache(cache, description, MapLikeCollection.lruLinkedHashMap(maxSize = 20))
            }
            members { cache, description ->
                MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap())
            }
            users { cache, description ->
                MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap())
            }
        }
        enableShutdownHook = true
    }
    webHost = embeddedServer(Netty, port = if(instanceBot.isTest) 8081 else 8080,
        host = "0.0.0.0", module = Application::module).start(false)

    logger = kordLogger

    if (instanceBot.isTest) {
        logger.warn("!!!TESTMODE!!!")
    }
    autoSave = DataManager.autoSave()
    //DataManager.dataCleanup(memberList)

    instance.on<Event> { Events.handle(this) }
    instance.login {
        intents = Intents(Intents.nonPrivileged + Intent.GuildMessages + Intent.Guilds)
    }
}
