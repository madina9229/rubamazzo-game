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


object Routes {
  def gameRoutes(games: scala.collection.mutable.Map[String, Game]): Route = {
    concat(
      path("createGame") {
        post {
          val gameId = createGame()
          complete(s"Game created with ID: $gameId")
        }
      },
      path("joinGame" / Segment) { gameId =>
        post {
          parameter("playerName") { playerName =>
            games.get(gameId) match {
              case Some(game) =>
                if (game.players.contains(playerName)) {
                  complete(StatusCodes.BadRequest, s"Player $playerName is already part of the game")
                } else {
                  val updatedGame = game.copy(players = game.players :+ playerName)
                  games += (gameId -> updatedGame)
                  complete(s"Player $playerName joined game with ID: $gameId")
                }
              case None =>
                complete(StatusCodes.NotFound, s"Game with ID $gameId not found")
            }
          }
        }
      },
      path("gameState" / Segment) { gameId =>
        get {
          games.get(gameId) match {
            case Some(game) =>
              complete(game.toJson.prettyPrint)
            case None =>
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
                    val updatedGame = game.copy(
                      players = game.players.filterNot(_ == playerName),
                      disconnectedPlayers = game.disconnectedPlayers :+ playerName
                    )
                    games += (gameId -> updatedGame)
                    complete(s"Player $playerName disconnected from game with ID: $gameId")
                  case Some(playerName) =>
                    complete(StatusCodes.BadRequest, s"Player $playerName is not part of the game")
                  case None =>
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
                val updatedGame = game.copy(
                  players = game.players :+ playerName,
                  disconnectedPlayers = game.disconnectedPlayers.filterNot(_ == playerName)
                )
                games += (gameId -> updatedGame)
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
                if (game.players(game.currentTurn) == playerName) {
                  // Logica per gestire la mossa qui (da implementare)
                  updateTurn(gameId) // Aggiorna il turno dopo la mossa
                  complete(s"Move accepted. Turn passed to the next player.")
                } else {
                  complete(StatusCodes.BadRequest, s"It's not $playerName's turn")
                }
              case None =>
                complete(StatusCodes.NotFound, s"Game with ID $gameId not found")
            }
          }
        }
      },
      path("currentTurn" / Segment) { gameId =>
        get {
          games.get(gameId) match {
            case Some(game) if game.players.isEmpty =>
              system.log.warning(s"Game $gameId has no players")
              complete(StatusCodes.BadRequest, "No players in the game")
            case Some(game) =>
              val currentPlayer = game.players(game.currentTurn)
              complete(s"It's $currentPlayer's turn")
            case None =>
              complete(StatusCodes.NotFound, s"Game with ID $gameId not found")
          }
        }
      },
      path("connect" / Segment) { playerName =>
        handleWebSocketMessages(webSocketFlow(playerName))
      }
    )

  }
}


