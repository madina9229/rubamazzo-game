package server
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import server.WebSocketHandler._
import server.Server
import server.Server._
import akka.http.scaladsl.model.StatusCodes
import spray.json.DefaultJsonProtocol
import spray.json._
import model.GameJsonProtocol._
import model.Game
import akka.actor.ActorSystem
import akka.event.Logging
import server.MoveManager
import server.GameManager


object Routes {

  private val system = ActorSystem("GameRoutes")
  private val log = Logging(system, getClass)

  def gameRoutes(games: scala.collection.mutable.Map[String, Game]): Route = {
    pathPrefix("game"){
      concat(
        path("createGame") {
          post {
            val gameId = GameManager.createGame()
            log.info(s"Game created with ID: $gameId. Current games: ${games.keys.mkString(", ")}")
            complete(s"Game created with ID: $gameId")
          }
        },
        path("join" / Segment / Segment) { (gameId, playerName) =>
          complete(GameManager.joinGame(gameId, playerName))
        },
        path("start" / Segment) { gameId =>
          complete(GameManager.startGame(gameId))
        },
        path("gameState" / Segment) { gameId =>
          get {
            games.get(gameId) match {
              case Some(game) =>
                val currentPlayerName = game.players.lift(game.currentTurn).getOrElse("Unknown")

                val customJson = game.toJson.asJsObject
                  .copy(fields = game.toJson.asJsObject.fields ++
                    Map("currentTurn" -> JsString(currentPlayerName),
                      "remainingDeck" -> JsArray(Vector(game.deck.map(card => JsString(card)): _*))
                    )
                  )
                complete(customJson.prettyPrint + s"\nDisconnected Players: ${game.disconnectedPlayers.mkString(", ")}")
              case None =>
                log.warning(s"Game with ID $gameId not found when fetching state")
                complete(StatusCodes.NotFound, s"Game with ID $gameId not found")
            }
          }
        },
        path("disconnectPlayer" / Segment) { gameId =>
          post {
            parameter("playerName") { playerName =>
              PlayerManager.handleDisconnection(GameManager.games, gameId, playerName)
              complete(s"Player $playerName disconnected from game with ID: $gameId.")
            }
          }
        },
        path("reconnectPlayer" / Segment) { gameId =>
          post {
            parameter("playerName") { playerName =>
              val response = PlayerManager.reconnectPlayer(GameManager.games, gameId, playerName)
              complete(response)
            }
          }
        },
        path("checkTimeout" / Segment) { playerName =>
          get {
            val lastAction = TimeoutManager.getLastAction(playerName)
            val timeoutDuration = 3600000
            val status = if (lastAction.exists(System.currentTimeMillis() - _ > timeoutDuration)) "Inactive" else "Active"
            complete(s"Player $playerName timeout status: $status")
          }
        },
        path("makeMove" / Segment) { gameId =>
          post {
            parameter("playerName", "move".as[String]) { (playerName, playedCard) =>
              games.get(gameId) match {
                case Some(game) if game.disconnectedPlayers.contains(playerName) =>
                  complete(StatusCodes.BadRequest, s"Player $playerName is disconnected and cannot make a move.")
                case Some(game) =>
                  log.info(s"Player $playerName attempting move '$playedCard' in game $gameId")
                  val result = MoveManager.handleMove(games, gameId, playerName, playedCard)
                  if (result.startsWith("Invalid")) {
                    log.warning(s"Move failed: $result")
                    complete(StatusCodes.BadRequest, result)
                  } else {
                    // Reset the timeout for the player after a valid move
                    TimeoutManager.recordAction(playerName)
                    TimeoutManager.scheduleTimeout(playerName, 3600000) {
                      PlayerManager.handleTimeout(GameManager.games, gameId, playerName)
                    }
                    log.info(s"Move succeeded: $result")
                    complete(result)
                  }
                case None =>
                  log.error(s"Game with ID $gameId not found when making a move")
                  complete(StatusCodes.NotFound, s"Game with ID $gameId not found")
              }
            }
          }
        },
        path("updateTurn" / Segment) { gameId =>
          complete(GameManager.updateTurn(gameId))
        },



          // WebSocket connection route
        path("connectPlayer" / Segment) { playerName =>
          log.info(s"Player $playerName is attempting to connect via WebSocket")
          handleWebSocketMessages(WebSocketHandler.webSocketFlow(GameManager.games, playerName)(system.dispatcher))
        },
        path("") {
          get {
            complete("Server is running!")
          }
        }

      )

    }

  }
}


