package server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import spray.json._
import scala.concurrent.ExecutionContext
import scala.io.StdIn
import model.Game
import model.GameJsonProtocol._
import akka.http.scaladsl.model.StatusCodes
import akka.event.Logging
import server.Routes
import scala.concurrent.duration._
import server.GameManager
import scala.concurrent.Await
import scala.collection.mutable
import java.time.Instant
import scala.concurrent.duration.Duration.Inf
import server.PlayerManager
import java.time.{Duration => JavaDuration}
import scala.concurrent.duration.Duration


object Server {

  // Global map to track the last ping of each client
  val lastSeen: mutable.Map[(String, String), Instant] = mutable.Map()

  def main(args: Array[String]): Unit = {
    System.setProperty("file.encoding", "UTF-8")
    implicit val system: ActorSystem = ActorSystem("RubamazzoServer")
    implicit val materializer: Materializer = Materializer(system)
    implicit val executionContext: ExecutionContext = system.dispatcher

    val log = Logging(system, getClass)

    val route: Route = Routes.gameRoutes(GameManager.games)

    // Graceful shutdown hook
    sys.addShutdownHook {
      log.info("Shutting down ActorSystem...")
      system.terminate()
    }

    // Start server
    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

    bindingFuture.onComplete {
      case scala.util.Success(_) =>
        log.info("Server successfully started at http://localhost:8080/game/")
        println("Server is now running.")
        println("Waiting for manual termination... Press Ctrl+C to stop.")
      case scala.util.Failure(ex) =>
        log.error(s"Server failed to start! Reason: ${ex.getMessage}")
        system.terminate()
    }

    // Timeout checker every 30 seconds
    system.scheduler.scheduleAtFixedRate(0.seconds, 30.seconds) { () =>
      val now = Instant.now()
      lastSeen.foreach { case ((gameId, playerName), lastTime) =>
        val elapsed = JavaDuration.between(lastTime, now).getSeconds
        if (elapsed > 30) {
          PlayerManager.handleDisconnection(GameManager.games, gameId, playerName)
          lastSeen.remove((gameId, playerName))
          println(s"$playerName from game $gameId disconnected due to $elapsed seconds inactivity.")

          // After disconnecting, check if there is only one active player left
          GameManager.games.get(gameId).foreach { game =>
            val activePlayers = game.players.diff(game.disconnectedPlayers)
            if (activePlayers.size == 1 && !game.gameOver) {
              println(s"Only one active player left in game $gameId. Triggering endGame.")
              GameManager.endGame(gameId)
            }
          }

        }
      }
    }


    // Periodic cleanup of abandoned games
    system.scheduler.scheduleAtFixedRate(initialDelay = 0.seconds, interval = 5.minutes) { () =>
      GameManager.games.keys.foreach(GameManager.checkGameStatus)
    }


    Await.ready(system.whenTerminated, Duration.Inf)
    log.info("Server terminated gracefully.")

  }

}
