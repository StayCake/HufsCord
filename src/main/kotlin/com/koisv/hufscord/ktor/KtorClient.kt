package com.koisv.hufscord.ktor

import dev.kord.core.kordLogger
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*

object KtorClient {
    //http 클라이언트
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            jackson {
            }
        }
        // 로깅 설정
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    kordLogger.trace(message)
                }
            }
            level = LogLevel.BODY
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 10000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 10000
        }

        install(WebSockets) {
            pingInterval = 15_000
        }

        // 기본적인 api 호출시 넣는 것들 즉, 기본 세팅
        defaultRequest {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }
    }
}