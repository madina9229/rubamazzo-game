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

object Server {

  def main(args: Array[String]): Unit = {
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
      case scala.util.Success(_) => log.info("Server successfully started at http://localhost:8080/")
      case scala.util.Failure(ex) =>
        log.error(s"Server failed to start! Reason: ${ex.getMessage}")
        system.terminate()
    }

    // Periodic cleanup of abandoned games
    system.scheduler.scheduleAtFixedRate(initialDelay = 0.seconds, interval = 5.minutes) { () =>
      GameManager.games.keys.foreach(GameManager.checkGameStatus)
    }

    println("Press RETURN to stop the server...")
    StdIn.readLine()

    bindingFuture
      .flatMap(_.unbind())
      .onComplete { _ =>
        log.info("Server stopped.")
        system.terminate()
      }
  }

}
