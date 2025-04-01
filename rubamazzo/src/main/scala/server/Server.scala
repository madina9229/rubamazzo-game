package server
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import spray.json._
import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn
import model.Game
import model.GameJsonProtocol
import akka.http.scaladsl.model.StatusCodes
import spray.json.DefaultJsonProtocol
import model.GameJsonProtocol._
import server.Routes
import akka.http.scaladsl.model.ws.TextMessage


object Server extends App {
  implicit val system: ActorSystem = ActorSystem("RubamazzoServer")
  implicit val materializer: Materializer = Materializer(system)
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher


  var games: scala.collection.mutable.Map[String, Game] = scala.collection.mutable.Map()
  val route: Route = Routes.gameRoutes(games)

  val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)




  def createGame(): String = {
    val gameId = java.util.UUID.randomUUID().toString
    games += (gameId -> Game(gameId, List()))
    gameId
  }

  def updateTurn(gameId: String): Unit = {
    games.get(gameId) match {
      case Some(game) =>
        val nextTurn = (game.currentTurn + 1) % game.players.size
        val updatedGame = game.copy(currentTurn = nextTurn)
        games += (gameId -> updatedGame)
      case None =>
        println(s"Game with ID $gameId not found")
    }
  }


  def reconnectPlayer(gameId: String, playerName: String): String = {
    games.get(gameId) match {
      case Some(game) =>
        if (game.players.contains(playerName)) {
          s"Player $playerName reconnected to game with ID: $gameId"
        } else {
          "Player not part of the game"
        }
      case None =>
        "Game not found"
    }
  }


  def handleDisconnection(gameId: String, playerName: String): Unit = {
    games.get(gameId) match {
      case Some(game) =>
        val updatedPlayers = game.players.filterNot(_ == playerName)
        val newTurn = if (game.players(game.currentTurn) == playerName) {
          game.currentTurn % updatedPlayers.size
        } else {
          game.currentTurn
        }
        val updatedGame = game.copy(players = updatedPlayers, currentTurn = newTurn)
        games += (gameId -> updatedGame)
        WebSocketHandler.broadcastToOtherClients(TextMessage(s"Player $playerName has disconnected from game with ID: $gameId"))

      case None =>
        println(s"Game with ID $gameId not found")
    }
  }


  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete { _ =>
      system.log.info("Server stopped.")
      system.terminate()
    }
}
