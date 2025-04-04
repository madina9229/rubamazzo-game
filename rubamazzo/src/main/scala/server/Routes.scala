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
                  complete(s"Player $playerName joined game with ID: $gameId")
                }
              case None =>
                log.error(s"Game with ID $gameId not found when attempting to join")
                complete(StatusCodes.NotFound, s"Game with ID $gameId not found")
            }
          }
        }
      },
      path("gameState" / Segment) { gameId =>
        get {
          games.get(gameId) match {
            case Some(game) =>
              log.info(s"Game state found: ${game.toJson.prettyPrint}")
              complete(game.toJson.prettyPrint)
            case None =>
              log.warning(s"Game with ID $gameId not found when fetching state")
              complete(StatusCodes.NotFound, s"Game with ID $gameId not found")
          }
        }
      },
      path("disconnectPlayer" / Segment) { gameId =>
        post {
          parameter("playerName".?) { playerNameOpt =>
            games.get(gameId) match {
              case Some(game) =>
                playerNameOpt match {
                  case Some(playerName) if game.players.contains(playerName) =>
                    log.info(s"Before disconnect: Players = ${game.players}, Disconnected = ${game.disconnectedPlayers}")
                    val updatedGame = game.copy(
                      players = game.players.filterNot(_ == playerName),
                      disconnectedPlayers = game.disconnectedPlayers :+ playerName
                    )
                    games += (gameId -> updatedGame)
                    log.info(s"After disconnect: Players = ${updatedGame.players}, Disconnected = ${updatedGame.disconnectedPlayers}")
                    log.info(s"Disconnecting player $playerName from game $gameId")

                    complete(s"Player $playerName disconnected from game with ID: $gameId")
                  case Some(playerName) =>
                    log.warning(s"Player $playerName is not part of game $gameId")
                    complete(StatusCodes.BadRequest, s"Player $playerName is not part of the game")
                  case None =>
                    log.error("Missing required query parameter 'playerName'")
                    complete(StatusCodes.BadRequest, "Missing required query parameter 'playerName'")

                }
              case None =>
                complete(StatusCodes.NotFound, s"Game with ID $gameId not found")
            }
          }
        }
      },
      path("reconnectPlayer" / Segment) { gameId =>
        post {
          parameter("playerName") { playerName =>
            games.get(gameId) match {
              case Some(game) if game.disconnectedPlayers.contains(playerName) =>
                log.info(s"Before reconnect: Players = ${game.players}, Disconnected = ${game.disconnectedPlayers}")
                val updatedGame = game.copy(
                  players = game.players :+ playerName,
                  disconnectedPlayers = game.disconnectedPlayers.filterNot(_ == playerName)
                )
                games += (gameId -> updatedGame)
                log.info(s"After reconnect: Players = ${updatedGame.players}, Disconnected = ${updatedGame.disconnectedPlayers}")
                log.info(s"Player $playerName reconnected to game $gameId")

                complete(s"Player $playerName reconnected to game with ID: $gameId")
              case Some(_) =>
                complete(StatusCodes.BadRequest, s"Player $playerName was not disconnected or does not belong to the game")
              case None =>
                complete(StatusCodes.NotFound, s"Game with ID $gameId not found")
            }
          }
        }
      },
      path("makeMove" / Segment) { gameId =>
        post {
          parameter("playerName", "move") { (playerName, move) =>
            games.get(gameId) match {
              case Some(game) =>
                log.info(s"Player $playerName attempting move '$move' in game $gameId")
                if (game.players(game.currentTurn) == playerName) {
                  // Logica per gestire la mossa qui (da implementare)
                  updateTurn(gameId) // Aggiorna il turno dopo la mossa
                  log.info(s"Move accepted. Turn passed to the next player.")
                  complete(s"Move accepted. Turn passed to the next player.")
                } else {
                  log.warning(s"Invalid move: It's not $playerName's turn in game $gameId")
                  complete(StatusCodes.BadRequest, s"It's not $playerName's turn")
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
      path("connect" / Segment) { playerName =>
        log.info(s"Player $playerName is attempting to connect via WebSocket")
        handleWebSocketMessages(webSocketFlow(playerName))
      },
      path("") {
        get {
          complete("Server is running!")
        }
      }

    )

  }
}


