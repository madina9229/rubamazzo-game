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
  val disconnectionTimeout = 60000 // 1 minute
  implicit val ec: ExecutionContext = system.dispatcher
  val previousTurnHolder: scala.collection.mutable.Map[String, String] = scala.collection.mutable.Map()

  /**
   * Allows a previously disconnected player to rejoin an existing game.
   * This method reintegrates the player into the game's active players list,
   * removes them from the disconnected players list, resets their timeout,
   * and restores their previous hand and captured decks if they reconnect in time.
   * If the player exceeds the timeout, their cards are reassigned,
   * and the game may end if only one player remains.
   * It ensures that the game's state is updated accordingly.
   *
   * Usage:
   * Typically invoked via the `reconnectPlayer` route, allowing players who were disconnected to rejoin the game.
   *
   * @param games      Map containing all active games.
   * @param gameId     The ID of the game the player wants to reconnect to.
   * @param playerName The name of the player attempting to reconnect.
   * @return A message confirming the reconnection or reporting any errors.
   *
   * Functionality:
   * - Checks whether the player exceeded the timeout and determines game closure if necessary.
   * - Restores the player's previous state (hand, captured decks) if they reconnect in time.
   * - Ensures the correct turn order is maintained after reconnection.
   * - Updates the game state and player lists accordingly.
   * - Handles the case where the player is treated as a new connection if previous data is unavailable.
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

              val updatedGame = game.copy(
                players = game.players.filterNot(_ == playerName)
              )
              games.update(gameId, updatedGame)
              log.info(s"[Reconnection] Updated game: ${updatedGame.toString}")


              if (updatedGame.players.size == 1) {
                // There is only one player, we assign the remaining cards and close the game
                log.info(s"Game $gameId has ended. Winner: ${updatedGame.players.head}")
                val lastPlayer = updatedGame.players.head
                val finalCapturedDecks = game.capturedDecks.updated(
                  lastPlayer,
                  game.capturedDecks.getOrElse(lastPlayer, List()) ++ game.tableCards ++ previousHand
                )
                log.info(s"Checking reconnection for player: $playerName. Current disconnected players: ${game.disconnectedPlayers.mkString(", ")}")
                games.update(gameId, game.copy(
                  tableCards = List(),
                  capturedDecks = finalCapturedDecks,
                  disconnectedPlayers = game.disconnectedPlayers.filterNot(_ == playerName),
                  gameOver = true
                ))
                GameManager.endGame(gameId)
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
                return s"Player $playerName exceeded timeout and was removed from game $gameId."
              }
            }




            // Valid reconnection

            log.info(s"Player $playerName reconnected in time. Removing from disconnected list and restoring their hand and captured cards.")

            val updatedDisconnectedPlayers = game.disconnectedPlayers.filterNot(_ == playerName)
            log.info(s"[DEBUG - Reconnection] State BEFORE reconnection of $playerName: current turn = ${game.currentTurn}, current players = ${game.players},disconnected players = ${game.disconnectedPlayers}")


            val updatedHands = game.playerHands.updated(playerName, previousHand)
            val updatedCapturedDecks = game.capturedDecks.updated(playerName, capturedDeck)

            val updatedGame = game.copy(
              disconnectedPlayers = updatedDisconnectedPlayers,
              playerHands = updatedHands,
              capturedDecks = updatedCapturedDecks,
            )

            games.update(gameId, updatedGame)
            log.info(s"[DEBUG - Reconnection] State after updatedGame in reconnection of ${updatedGame.players}: current turn = ${updatedGame.currentTurn}, current players = ${updatedGame.players}, disconnected players = ${updatedGame.disconnectedPlayers}")

            games.get(gameId) match {
              case Some(refreshedGame) =>
                val result = GameManager.updateTurn(gameId)
                log.info(s"[Reconnection] Turn update result: $result")
                val curr = refreshedGame.players.lift(refreshedGame.currentTurn).getOrElse("No players")
                log.info(s"[Reconnection] After updateTurn, current turn ----->>>: $curr, players: ${refreshedGame.players}")

              case None =>
                log.warning(s"[Reconnection] Game $gameId unexpectedly missing after update.")
            }

            disconnectedPlayerData.remove(playerName)

            log.info(s"[Reconnection] Player $playerName successfully reconnected. Game continues.")

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
   * If only one player remains, the game waits for a reconnection timeout before concluding.
   *
   * Usage:
   * Typically invoked when a player disconnects manually or times out due to inactivity.
   *
   * @param games      Map containing all active games.
   * @param gameId     The ID of the game the player is disconnecting from.
   * @param playerName The name of the player being disconnected.
   *
   * Functionality:
   *  - Ensures the player is not already in the disconnected list.
   *  - Logs the disconnection event and removes the player from tracking.
   *  - Saves the player's current state (hand, captured cards, disconnection time).
   *  - Maintains the original turn order for potential reconnections.
   *  - Removes the player from the active list and updates the game's state accordingly.
   *  - If only one player remains, waits for a timeout before finalizing the game.
   *  - If the disconnected player was on turn, prevents immediate turn update.
   *  - Otherwise, invokes GameManager to proceed with turn updates.
   */
  def handleDisconnection(games: scala.collection.mutable.Map[String, Game], gameId: String, playerName: String): Unit = {
    games.get(gameId) match {
      case Some(game) =>
        if (!game.disconnectedPlayers.contains(playerName)) {
          log.info(s"Player $playerName disconnected from game $gameId.")
          log.info(s"[handleDisconnection] Removed player $playerName from tracking BEFORE saving disconnect state.")
          // Save the disconnected player's state
          val disconnectTime = System.currentTimeMillis()
          disconnectedPlayerData(playerName) = (
            game.playerHands(playerName),
            game.capturedDecks.getOrElse(playerName, List()),
            disconnectTime
          )
          log.info(s"[handleDisconnection] Saved player state for $playerName - Hand: ${disconnectedPlayerData(playerName)._1}, Captured: ${disconnectedPlayerData(playerName)._2}, Timestamp: ${disconnectedPlayerData(playerName)._3}")
          // Store the player's original index in GameManager before removing them
          GameManager.originalPlayerOrders.get(gameId) match {
            case Some(originalOrder) =>
              val originalIndex = originalOrder.getOrElse(playerName, game.players.size)
              GameManager.originalPlayerOrders.update(gameId, originalOrder.updated(playerName, originalIndex))
            case None =>
              log.warning(s"No original order found for game $gameId.")
          }

          // Remove player from the active list
          val updatedDisconnectedPlayers =  if (!game.disconnectedPlayers.contains(playerName)) game.disconnectedPlayers :+ playerName else game.disconnectedPlayers
          var remainingCards = game.playerHands.getOrElse(playerName, List())

          if (game.players.size == 1) {
            val lastPlayer = game.players.head
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
                  disconnectedPlayers = game.disconnectedPlayers.filterNot(_ == playerName),
                  gameOver = true
                ))
                GameManager.endGame(gameId)
                disconnectedPlayerData.remove(playerName)

              }
            }
          }

          log.info(s"Game $gameId. Players: ${game.players.mkString(", ")}.")
          val updatedGame = game.copy(
            disconnectedPlayers = updatedDisconnectedPlayers,
            playerHands = game.playerHands - playerName
          )
          games += (gameId -> updatedGame)


          //GameManager.updateTurn(gameId)
          log.info(s"[handleDisconnection] current turn:: ${updatedGame.players.lift(updatedGame.currentTurn).getOrElse("No Players")}")
          log.info(s"[handleDisconnection] current turn:: ${updatedGame.players.lift(updatedGame.currentTurn).getOrElse("No Players")}")

          if (updatedGame.players.lift(updatedGame.currentTurn).getOrElse("") == playerName) {
            log.info(s"[handleDisconnection] $playerName was on turn before disconnection, so NOT updating turn")

          } else {
            log.info(s"[handleDisconnection] Il turno di $playerName è già passato, aggiorno al prossimo giocatore...")
            GameManager.updateTurn(gameId)
          }

          log.info(s"Player $playerName Players: ${updatedGame.players.mkString(", ")}.")
          println(s"Player $playerName temporarily disconnected from game $gameId.")

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


    // Update the game state for the specified game
    games.get(gameId) match {
      case Some(game) =>
        handleDisconnection(games, gameId, playerName) // Handle disconnection for this game
      case None =>
        log.warning(s"Game with ID $gameId not found when handling timeout.")
    }

  }


}


