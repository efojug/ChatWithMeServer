import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    embeddedServer(Netty, port = 4380, host = "0.0.0.0") {
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 15.seconds
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
        routing {
            webSocket("/chat") {
                println("Client connected: ${this.call.request.origin.remoteHost}")
                ChatServer.addSession(this)
                try {
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            println("Received: $text")
                            ChatServer.broadcast(text, this)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    ChatServer.removeSession(this)
                }
            }
        }
    }.start(wait = true)
}

object ChatServer {
    private val sessions = mutableSetOf<DefaultWebSocketServerSession>()
    fun addSession(session: DefaultWebSocketServerSession) {
        sessions.add(session)
    }

    fun removeSession(session: DefaultWebSocketServerSession) {
        sessions.remove(session)
    }

    suspend fun broadcast(message: String, sender: DefaultWebSocketServerSession) {
        sessions.forEach { session ->
            if (session != sender) {
                session.send(Frame.Text(message))
            }
        }
    }
}