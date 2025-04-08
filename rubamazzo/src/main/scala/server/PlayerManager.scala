package server


import model.Game
import server.GameManager
import akka.http.scaladsl.model.ws.TextMessage
import akka.actor.ActorSystem
import akka.event.Logging


object PlayerManager {

  private val system = ActorSystem("PlayerManager")
  private val log = Logging(system, getClass)

  /**
   * Allows a previously disconnected player to rejoin an existing game.
   * This method reintegrates the player into the game's active players list,
   * removes them from the disconnected players list, and resets their timeout.
   * It ensures that the game's state is updated accordingly.
   *
   * Usage:
   * Typically invoked via the `reconnectPlayer` route, allowing players who were disconnected to rejoin the game.
   *
   * @param gameId     The ID of the game the player wants to reconnect to.
   * @param playerName The name of the player attempting to reconnect.
   * @return A message confirming the reconnection or reporting any errors.
   */
  def reconnectPlayer(games: scala.collection.mutable.Map[String, Game], gameId: String, playerName: String): String = {
    games.get(gameId) match {
      case Some(game) if game.disconnectedPlayers.contains(playerName) =>
        val updatedGame = game.copy(
          players = game.players :+ playerName,
          disconnectedPlayers = game.disconnectedPlayers.filterNot(_ == playerName)
        )
        games.update(gameId, updatedGame)
        println(s"Player $playerName reconnected to game $gameId.")
        TimeoutManager.recordAction(playerName)
        TimeoutManager.scheduleTimeout(playerName, 60000) { // 60-second timeout
          handleTimeout(games, gameId, playerName)
        }
        s"Player $playerName reconnected to game with ID: $gameId."
      case Some(_) =>
        s"Player $playerName was not disconnected or does not belong to this game."
      case None =>
        s"Game with ID $gameId not found."
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
   * @param gameId     The ID of the game the player is disconnecting from.
   * @param playerName The name of the player being disconnected.
   */
  def handleDisconnection(games: scala.collection.mutable.Map[String, Game], gameId: String, playerName: String): Unit = {
    games.get(gameId) match {
      case Some(game) =>
        // Remove the disconnected player from the player list
        val updatedPlayers = game.players.filterNot(_ == playerName)
        // Adjust the turn order if the disconnected player was the current player
        val newTurn = if (game.players(game.currentTurn) == playerName) {
          game.currentTurn % updatedPlayers.size
        } else {
          game.currentTurn
        }
        val updatedGame = game.copy(
          players = updatedPlayers,
          disconnectedPlayers = game.disconnectedPlayers :+ playerName,
          currentTurn = newTurn)
        games += (gameId -> updatedGame)
        // Broadcast the disconnection event to other clients via WebSocket
        WebSocketHandler.broadcastToOtherClients(
          TextMessage(s"Player $playerName has disconnected from game with ID: $gameId")
        )

        TimeoutManager.removePlayer(playerName)
        println(s"Player $playerName has been removed from game $gameId. Remaining players: ${updatedPlayers.mkString(", ")}.")

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
   * @param playerName The name of the player performing the action.
   */
  def onPlayerAction(games: scala.collection.mutable.Map[String, Game], playerName: String): Unit = {
    TimeoutManager.recordAction(playerName)
    // Schedule a timeout for the player to monitor inactivity
    TimeoutManager.scheduleTimeout(playerName, 60000) {
      games.foreach { case (id, game) =>
        if (game.players.contains(playerName)) {
          handleTimeout(games, id, playerName)
        }
      }
    }
  }


}


