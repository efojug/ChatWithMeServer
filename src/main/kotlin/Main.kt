import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
data class User(val id: Int, val username: String, val password: String)

@Serializable
data class RegisterRequest(val username: String, val password: String)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(val userId: Int, val username: String, val token: String)

@Serializable
data class Message(val userId: Int, val content: String, val timestamp: Long = System.currentTimeMillis())

object UserStorage{
    private val users = mutableMapOf<String, User>()
    private var nextId = 1

    fun register(username: String, password: String): User? {
        if (users.containsKey(username)) return null
        val user = User(nextId++, username, password)
        users[username] = user
        return user
    }

    fun login(username: String, password: String): User? {
        val user = users[username]
        return if (user != null && user.password == password) user else null
    }
}

fun main(args: Array<String>) {
    embeddedServer(Netty, port = 4380, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json()
        }
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 15.seconds
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
        routing {
            //register
            post("/register") {
                val request = call.receive<RegisterRequest>()
                val user = UserStorage.register(request.username, request.password)
                if (user == null) {
                    call.respond(HttpStatusCode.Conflict, "Username already exists")
                } else {
                    call.respond(HttpStatusCode.OK, "Registered successfully")
                }
                }

            post("/login") {
                val request = call.receive<LoginRequest>()
                val user = UserStorage.login(request.username, request.password)
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid username or password")
                } else {
                    call.respond(HttpStatusCode.OK, LoginResponse(user.id, user.username, "dummy-token"))
                }
            }

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