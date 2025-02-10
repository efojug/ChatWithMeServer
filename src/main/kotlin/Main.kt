import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.time.Duration

fun main(args: Array<String>) {
    embeddedServer(Netty, port = 4380, host = "0.0.0.0") {
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(10)
            timeout = Duration.ofSeconds(10)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
        routing {
            webSocket("/chat") {
                println("Client connected: ${this.call.request.origin.remoteHost}")

            }
        }
    }
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