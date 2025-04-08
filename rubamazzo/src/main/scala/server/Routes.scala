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

object Routes {

  private val system = ActorSystem("GameRoutes")
  private val log = Logging(system, getClass)

  def gameRoutes(games: scala.collection.mutable.Map[String, Game]): Route = {
    concat(
      path("createGame") {
        post {
          val gameId = createGame()
          log.info(s"Game created with ID: $gameId. Current games: ${games.keys.mkString(", ")}")
          complete(s"Game created with ID: $gameId")
        }
      },
      path("joinGame" / Segment) { gameId =>
        post {
          parameter("playerName") { playerName =>
            games.get(gameId) match {
              case Some(game) =>
                log.info(s"Before join: Players in game $gameId: ${game.players}")
                if (game.players.contains(playerName)) {
                  log.warning(s"Player $playerName is already in game $gameId")
                  complete(StatusCodes.BadRequest, s"Player $playerName is already part of the game")
                } else {
                  val updatedGame = game.copy(players = game.players :+ playerName)
                  games += (gameId -> updatedGame)
                  log.info(s"After join: Players in game $gameId: ${updatedGame.players}")

                  // Register the player in the TimeoutManager
                  TimeoutManager.recordAction(playerName)
                  TimeoutManager.scheduleTimeout(playerName, 60000) {
                    handleTimeout(gameId, playerName)
                  }

                  complete(s"Player $playerName joined game with ID: $gameId")
                }
              case None =>
                log.error(s"Game with ID $gameId not found when attempting to join")
                complete(StatusCodes.NotFound, s"Game with ID $gameId not found")
            }
          }
        }
      },
      path("startGame" / Segment) { gameId =>
        post {
          games.get(gameId) match {
            case Some(game) if game.players.nonEmpty =>
              dealCards(gameId) // Distribute cards to players
              log.info(s"Game $gameId started with players: ${game.players.mkString(", ")}")
              complete(s"Game $gameId has started.")
            case Some(_) =>
              log.warning(s"Game $gameId cannot start because no players have joined.")
              complete(StatusCodes.BadRequest, "No players have joined the game yet.")
            case None =>
              log.error(s"Game with ID $gameId not found when starting the game.")
              complete(StatusCodes.NotFound, s"Game with ID $gameId not found.")
          }
        }
      },
      path("gameState" / Segment) { gameId =>
        get {
          games.get(gameId) match {
            case Some(game) =>
              val fullGameState = game.toJson.prettyPrint +
                s"\nDisconnected Players: ${game.disconnectedPlayers.mkString(", ")}"
              complete(fullGameState)
            case None =>
              log.warning(s"Game with ID $gameId not found when fetching state")
              complete(StatusCodes.NotFound, s"Game with ID $gameId not found")
          }
        }
      },
      path("disconnectPlayer" / Segment) { gameId =>
        post {
          parameter("playerName") { playerName =>
            handleDisconnection(gameId, playerName)
            complete(s"Player $playerName disconnected from game with ID: $gameId.")
          }
        }
      },
      path("reconnectPlayer" / Segment) { gameId =>
        post {
          parameter("playerName") { playerName =>
            val response = reconnectPlayer(gameId, playerName)
            complete(response)
          }
        }
      },
      path("checkTimeout" / Segment) { playerName =>
        get {
          val lastAction = TimeoutManager.getLastAction(playerName)
          val timeoutDuration = 60000 // 60-second timeout
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
                val result = handleMove(gameId, playerName, playedCard)
                  if (result.startsWith("Invalid")) {
                    log.warning(s"Move failed: $result")
                    complete(StatusCodes.BadRequest, result)
                  } else {
                    // Reset the timeout for the player after a valid move
                    TimeoutManager.recordAction(playerName)
                    TimeoutManager.scheduleTimeout(playerName, 60000) {
                      handleTimeout(gameId, playerName)
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
      path("currentTurn" / Segment) { gameId =>
        get {
          log.info(s"Received request to fetch current turn for game ID: $gameId")
          games.get(gameId) match {
            case Some(game) if game.players.isEmpty =>
              log.warning(s"Game $gameId has no players")
              complete(StatusCodes.BadRequest, "No players in the game")
            case Some(game) =>
              log.info(s"Game $gameId found. Players: ${game.players}, Current turn: ${game.currentTurn}")
              val currentPlayer = game.players(game.currentTurn)
              log.info(s"Current player for game $gameId is $currentPlayer")
              complete(s"It's $currentPlayer's turn")
            case None =>
              log.error(s"Game with ID $gameId not found")
              complete(StatusCodes.NotFound, s"Game with ID $gameId not found")
          }
        }
      },
      // WebSocket connection route
      path("connectPlayer" / Segment) { playerName =>
        log.info(s"Player $playerName is attempting to connect via WebSocket")
        handleWebSocketMessages(WebSocketHandler.webSocketFlow(games, playerName)(system.dispatcher))
      },
      path("") {
        get {
          complete("Server is running!")
        }
      }

    )

  }
}


