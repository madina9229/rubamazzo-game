package server
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import spray.json._
import akka.http.scaladsl.server.Directives._
import scala.concurrent.ExecutionContext
import scala.io.StdIn
import model.Game
import model.GameJsonProtocol
import akka.http.scaladsl.model.StatusCodes
import spray.json.DefaultJsonProtocol
import model.GameJsonProtocol._
import server.Routes
import akka.http.scaladsl.model.ws.TextMessage
import scala.concurrent.duration._
import akka.event.Logging
import server.GameManager

object Server extends App {

  implicit val system: ActorSystem = ActorSystem("RubamazzoServer")
  implicit val materializer: Materializer = Materializer(system)
  implicit val executionContext: ExecutionContext = system.dispatcher

  private val log = Logging(system, getClass)

  val route: Route = Routes.gameRoutes(GameManager.games)


  // Graceful shutdown hook
  sys.addShutdownHook {
    println("Shutting down ActorSystem...")
    system.terminate()
  }

  // Route configuration
  val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

  // Scheduler for periodic cleanup of abandoned games
  system.scheduler.scheduleAtFixedRate(initialDelay = 0.seconds, interval = 5.minutes) { () =>
    GameManager.games.keys.foreach { gameId =>
      GameManager.checkGameStatus(gameId)
    }
  }


  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete { _ =>
      system.log.info("Server stopped.")
      println("Server stopped.")
    }



}


