package server


import model.Game
import server.GameManager
import akka.http.scaladsl.model.ws.TextMessage
import akka.actor.ActorSystem
import akka.event.Logging
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object PlayerManager {

  private val system = ActorSystem("PlayerManager")
  private val log = Logging(system, getClass)
  val disconnectedPlayerData = scala.collection.mutable.Map[String, (List[String], List[String], Long)]()
  val disconnectionTimeout = 120000 // 2 minuti
  implicit val ec: ExecutionContext = system.dispatcher

  /**
   * Allows a previously disconnected player to rejoin an existing game.
   * This method reintegrates the player into the game's active players list,
   * removes them from the disconnected players list, and resets their timeout.
   * It ensures that the game's state is updated accordingly.
   *
   * Usage:
   * Typically invoked via the `reconnectPlayer` route, allowing players who were disconnected to rejoin the game.
   *
   * @param games      Map containing all active games.
   * @param gameId     The ID of the game the player wants to reconnect to.
   * @param playerName The name of the player attempting to reconnect.
   * @return A message confirming the reconnection or reporting any errors.
   */
  def reconnectPlayer(games: scala.collection.mutable.Map[String, Game], gameId: String, playerName: String): String = {
    games.get(gameId) match {
      case Some(game) =>
        val currentTime = System.currentTimeMillis()
        log.info(s"[TIME DEBUG] Current system time: $currentTime")
        log.info(s"Player $playerName is trying to reconnect to game $gameId.")

        disconnectedPlayerData.get(playerName) match {
          case Some((previousHand, capturedDeck, disconnectTime)) =>
            log.info(s"[TIME DEBUG] Stored disconnectTime for $playerName: $disconnectTime")
            val timeElapsed = System.currentTimeMillis() - disconnectTime
            log.info(s"Player $playerName was disconnected for $timeElapsed milliseconds (timeout limit: $disconnectionTimeout).")

            // If the timeout expires, handle the case based on the number of players
            if (timeElapsed >= disconnectionTimeout) {
              log.info(s"Player $playerName exceeded timeout and cannot reconnect.")
              log.info(s"End of game check. Players remaining: ${game.players.size}. Closure control...")
              if (game.players.size == 1) {
                // There is only one player, we assign the remaining cards and close the game
                log.info(s"Game $gameId has ended. Winner: ${game.players.head}")
                val lastPlayer = game.players.head
                val finalCapturedDecks = game.capturedDecks.updated(
                  lastPlayer,
                  game.capturedDecks.getOrElse(lastPlayer, List()) ++ game.tableCards ++ previousHand
                )
                log.info(s"Checking reconnection for player: $playerName. Current disconnected players: ${game.disconnectedPlayers.mkString(", ")}")
                games.update(gameId, game.copy(
                  tableCards = List(),
                  capturedDecks = finalCapturedDecks,
                  disconnectedPlayers = game.disconnectedPlayers.filterNot(_ == playerName)
                ))

                WebSocketHandler.broadcastToOtherClients(
                  "Server",
                  TextMessage(s"Game $gameId is ending. Final player $lastPlayer receives remaining cards.")
                )

                GameManager.endGame(gameId)
                games -= gameId
                disconnectedPlayerData.remove(playerName)
                return s"Game $gameId has ended because only one player remained."
              } else {
                // There are other players, assign the remaining cards
                log.info(s"Player $playerName is removed from game $gameId and their cards are reassigned.")

                val updatedDeck = game.deck ++ previousHand ++ capturedDeck
                val updatedGame = game.copy(
                  playerHands = game.playerHands - playerName,
                  capturedDecks = game.capturedDecks - playerName,
                  deck = updatedDeck,
                  disconnectedPlayers = game.disconnectedPlayers :+ playerName,
                  players = game.players.filterNot(_ == playerName)
                )

                games.update(gameId, updatedGame)
                disconnectedPlayerData.remove(playerName)

                WebSocketHandler.broadcastToOtherClients(
                  "Server",
                  TextMessage(s"Player $playerName could not reconnect and was removed from the game.")
                )

                return s"Player $playerName exceeded timeout and was removed from game $gameId."
              }
            }

            // Valid reconnection
            val wasTurnOfDisconnectedPlayer = game.players(game.currentTurn) == playerName
            TimeoutManager.removePlayer(playerName)
            require(TimeoutManager.getLastAction(playerName).isEmpty, s"Timeout should be removed after successful reconnection.")
            TimeoutManager.recordAction(playerName)

            log.info(s"Player $playerName reconnected in time. Removing from disconnected list and restoring their hand and captured cards.")

            val updatedDisconnectedPlayers = game.disconnectedPlayers.filterNot(_ == playerName)
            // Retrieve the original player order from GameManager
            val originalOrder = GameManager.originalPlayerOrders.getOrElse(gameId, Map())
            val originalIndex = originalOrder.getOrElse(playerName, game.players.size)
            val filteredPlayers = game.players.filterNot(_ == playerName)
            // Reinstate palyer in his correct position
            val updatedPlayers = if (originalIndex >= 0 && originalIndex < filteredPlayers.length) {
              filteredPlayers.patch(originalIndex, List(playerName), 0)
            } else {
              filteredPlayers :+ playerName
            }
            val updatedHands = game.playerHands.updated(playerName, previousHand)
            val updatedCapturedDecks = game.capturedDecks.updated(playerName, capturedDeck)

            val updatedGame = game.copy(
              players = updatedPlayers,
              disconnectedPlayers = updatedDisconnectedPlayers,
              playerHands = updatedHands,
              capturedDecks = updatedCapturedDecks
            )

            games.update(gameId, updatedGame)
            disconnectedPlayerData.remove(playerName)
            GameManager.updateTurn(gameId)
            log.info(s"[Reconnection] Player $playerName successfully reconnected. Game continues.")
            WebSocketHandler.broadcastToOtherClients(
              "Server",
              TextMessage(s"Player $playerName has reconnected to game $gameId.")
            )

            log.info(s"[Reconnection] Reconnection process completed for player $playerName.")
            return s"Player $playerName successfully reconnected to game with ID: $gameId."
        case None =>
         log.warning(s"[Reconnection] No previous data found for player $playerName. Treating as new connection.")
         s"Player $playerName is treated as a new connection."
       }
      case None =>
        log.warning(s"[Reconnection] Game with ID $gameId not found.")
        return s"Game with ID $gameId not found."
    }
  }


  /**
   * Handles the disconnection of a player from an active game.
   * This method updates the game's state to reflect the player's removal,
   * adjusts the turn order if necessary, and notifies other connected players about the disconnection.
   *
   * Usage:
   * Typically invoked when a player disconnects manually or times out due to inactivity.
   *
   * @param games      Map containing all active games.
   * @param gameId     The ID of the game the player is disconnecting from.
   * @param playerName The name of the player being disconnected.
   */
  def handleDisconnection(games: scala.collection.mutable.Map[String, Game], gameId: String, playerName: String): Unit = {
    games.get(gameId) match {
      case Some(game) =>
        if (!game.disconnectedPlayers.contains(playerName)) {
          log.info(s"Player $playerName disconnected from game $gameId.")
          TimeoutManager.removePlayer(playerName)
          require(TimeoutManager.getLastAction(playerName).isEmpty, s"Timeout should be removed after player $playerName disconnects.")
          log.info(s"[Timeout Manager] Removed player $playerName from tracking BEFORE saving disconnect state.")
          // Save the disconnected player's state
          val disconnectTime = System.currentTimeMillis()
          disconnectedPlayerData(playerName) = (
            game.playerHands(playerName),
            game.capturedDecks.getOrElse(playerName, List()),
            disconnectTime
          )
          log.info(s"[Disconnection] Saved player state for $playerName - Hand: ${disconnectedPlayerData(playerName)._1}, Captured: ${disconnectedPlayerData(playerName)._2}, Timestamp: ${disconnectedPlayerData(playerName)._3}")
          // Store the player's original index in GameManager before removing them
          GameManager.originalPlayerOrders.get(gameId) match {
            case Some(originalOrder) =>
              val originalIndex = originalOrder.getOrElse(playerName, game.players.size)
              GameManager.originalPlayerOrders.update(gameId, originalOrder.updated(playerName, originalIndex))
            case None =>
              log.warning(s"No original order found for game $gameId.")
          }

          // Remove player from the active list
          val updatedPlayers = game.players.filterNot(_ == playerName)
          val updatedDisconnectedPlayers =  if (!game.disconnectedPlayers.contains(playerName)) game.disconnectedPlayers :+ playerName else game.disconnectedPlayers
          var remainingCards = game.playerHands.getOrElse(playerName, List())
          //val activePlayers = updatedPlayers.filterNot(player => updatedDisconnectedPlayers.contains(player))
          //val sortedPlayers = activePlayers.sortBy(player => game.capturedDecks.getOrElse(player, List()).size)


          if (updatedPlayers.size == 1) {
            val lastPlayer = updatedPlayers.head
            log.info(s"Game $gameId has only one player remaining ($lastPlayer). Waiting for reconnection timeout...")
            system.scheduler.scheduleOnce(disconnectionTimeout.millis) {
              if (disconnectedPlayerData.contains(playerName)) {
                log.info(s"Player $playerName did not reconnect in time. Ending game $gameId.")
                val finalCapturedDecks = game.capturedDecks.updated(
                  lastPlayer,
                  game.capturedDecks.getOrElse(lastPlayer, List()) ++ game.tableCards ++ remainingCards
                )
                games.update(gameId, game.copy(
                  tableCards = List(),
                  capturedDecks = finalCapturedDecks,
                  disconnectedPlayers = game.disconnectedPlayers.filterNot(_ == playerName)
                ))
                WebSocketHandler.broadcastToOtherClients(
                  "Server",
                  TextMessage(s"Game $gameId has ended. $lastPlayer is the winner and receives the remaining cards.")
                )
                GameManager.endGame(gameId)
                games -= gameId
                disconnectedPlayerData.remove(playerName)

              }
            }
          }

          log.info(s"Game $gameId continues with remaining players: ${updatedPlayers.mkString(", ")}.")
          val updatedGame = game.copy(
            players = updatedPlayers,
            disconnectedPlayers = updatedDisconnectedPlayers,
            //currentTurn = newTurn,
            playerHands = game.playerHands - playerName
          )
          games += (gameId -> updatedGame)
          //GameManager.updateTurn(gameId)
          WebSocketHandler.broadcastToOtherClients(
            playerName,
            TextMessage(s"Player $playerName has disconnected from game $gameId. Game continues with players: ${updatedPlayers.mkString(", ")}.")
          )
          log.info(s"Player $playerName has been removed. Remaining players: ${updatedPlayers.mkString(", ")}.")
          println(s"Player $playerName has been removed from game $gameId. Remaining players: ${updatedPlayers.mkString(", ")}.")

        } else {
          log.warning(s"Player $playerName is already disconnected from game $gameId.")
        }
      case None =>
        println(s"Game with ID $gameId not found")
    }
  }


  /**
   * Handles the timeout event for a player due to inactivity.
   * This method is triggered when a player's timeout expires, disconnecting them from active games
   * and notifying other players about the disconnection.
   *
   * Usage:
   * Invoked by the `TimeoutManager.scheduleTimeout` mechanism when a player remains inactive
   * beyond the specified timeout duration.
   *
   * @param games      Map containing all active games.
   * @param gameId     The ID of the game the player belongs to (if timeout is game-specific).
   * @param playerName The name of the player who timed out due to inactivity.
   */
  def handleTimeout(games: scala.collection.mutable.Map[String, Game], gameId: String, playerName: String): Unit = {
    log.info(s"Player $playerName timed out due to inactivity.")

    // Remove the player's WebSocket connection
    WebSocketHandler.removeConnection(games, playerName)

    // Update the game state for the specified game
    games.get(gameId) match {
      case Some(game) =>
        handleDisconnection(games, gameId, playerName) // Handle disconnection for this game
      case None =>
        log.warning(s"Game with ID $gameId not found when handling timeout.")
    }

    // Notify other clients about the player's disconnection
    WebSocketHandler.broadcastToOtherClients(
      playerName,
      TextMessage(s"Player $playerName has been disconnected due to inactivity.")
    )
  }


  /**
   * Resets the timeout for a player when they perform an action.
   * This method ensures that active players are not mistakenly disconnected due to inactivity.
   *
   * Usage:
   * This method should be called whenever a player takes a meaningful action, such as making a move.
   * It updates the player's last action timestamp and schedules a timeout to disconnect the player
   * if they remain inactive beyond the specified duration.
   *
   * @param games Map containing all active games.
   * @param playerName The name of the player performing the action.
   */
  def onPlayerAction(games: scala.collection.mutable.Map[String, Game], playerName: String): Unit = {
    TimeoutManager.recordAction(playerName)
    // Schedule a timeout for the player to monitor inactivity
    TimeoutManager.scheduleTimeout(playerName, 3600000) {
      games.foreach { case (id, game) =>
        if (game.players.contains(playerName)) {
          handleTimeout(games, id, playerName)
        }
      }
    }
  }


}


