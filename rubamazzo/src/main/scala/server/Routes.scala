package server
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
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
import utils.Utils.displayCard
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes



object Routes {

  private val system = ActorSystem("GameRoutes")
  private val log = Logging(system, getClass)
  private val hearts = '\u2665'
  private val diamonds = '\u2666'
  private val clubs = '\u2663'
  private val spades = '\u2660'

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

        path("gameState" / Segment / Segment) { (gameId: String, playerName: String) =>
          get {
            games.get(gameId) match {
              case Some(game) =>


                val currentPlayerName = game.players.lift(game.currentTurn).getOrElse("Unknown")
                val tableCards = game.tableCards.map(displayCard).mkString("   ")
                val playerHand = game.playerHands.get(playerName).getOrElse(List()).map(displayCard).mkString("   ")
                val capturedCards = game.capturedDecks.get(playerName).getOrElse(List()).map(displayCard).mkString("   ")
                val capturedCardsByPlayers = game.players.filter(_ != playerName).map { player =>
                  val captured = game.capturedDecks.get(player).getOrElse(List()).map(displayCard).mkString("   ")
                  f"$player:\n${if (captured.isEmpty) "[None]" else captured}"
                }.mkString("\n\n")
                val disconnectedPlayers = game.disconnectedPlayers.mkString(", ")
                val disconnectedInfo = if (disconnectedPlayers.nonEmpty) s"\n>>Disconnected players: $disconnectedPlayers" else ""
                val remainingDeckInfo = s"\n>>Cards left in deck: ${game.deck.size}"
                val playerList = game.players.mkString(", ")


                val legend =
                  s"""
                 -------------------------------------
                 **Card Suit Legend**
                 Coppe  : $hearts
                 Denari: $diamonds
                 Bastoni   : $clubs
                 Spade  : $spades
                 """

                val winnerInfo = game.winner.map(w => s"GAME OVER! Winner: $w").getOrElse("GAME OVER! No winner could be determined.")
                val finalScores = game.players.map { player =>
                  s"$player: ${game.capturedDecks.getOrElse(player, List()).size} cards"
                }.mkString("\n")

                val gameStateHeader =
                  if (game.gameOver)
                    s"""
                    \n##########################################
                    \n**GAME OVER**
                    \n$winnerInfo

                    \n**Final Scores:**
                    \n$finalScores
                    \n##########################################
                    """
                  else s"\n***************************GAME STATE***************************\n\n>>Current Turn: $currentPlayerName"

                val formattedState =
                  s"""
                   \n$gameStateHeader

                   \n>>Players in the game:
                   \n$playerList

                   \n>>Cards on the table:
                   \n$tableCards

                   \n>>Your hand (${playerName}):
                   \n$playerHand

                   ${
                      if (game.capturedDecks.get(playerName).exists(_.nonEmpty))
                        s"\n>>Cards captured by you:\n${game.capturedDecks(playerName).map(displayCard).mkString("   ")}\n"
                      else ""
                    }

                   ${
                      if (capturedCardsByPlayers.nonEmpty)
                        s"\n>>Cards captured by other players:\n$capturedCardsByPlayers\n"
                      else ""
                    }
                   \n$remainingDeckInfo
                   \n$disconnectedInfo

                   \n$legend
                   """

                complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, formattedState))
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
        path("") {
          get {
            complete("Server is running!")
          }
        }

      )

    }

  }
}


