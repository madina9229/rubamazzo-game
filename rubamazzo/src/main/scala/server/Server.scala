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


object Server extends App {
  implicit val system: ActorSystem = ActorSystem("RubamazzoServer")
  implicit val materializer: Materializer = Materializer(system)
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val log: akka.event.LoggingAdapter = Logging(system, getClass)



  sys.addShutdownHook {
    println("Shutting down ActorSystem...")
    system.terminate()
  }

  var games: scala.collection.mutable.Map[String, Game] = scala.collection.mutable.Map()
  val route: Route = Routes.gameRoutes(games)

  val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

  // Scheduler for periodic cleanup of abandoned games
  system.scheduler.scheduleAtFixedRate(initialDelay = 0.seconds, interval = 5.minutes) { () =>
    games.keys.foreach(checkGameStatus) // Check the status of each game periodically
  }


    /**
   * Creates a new game and returns the unique game ID.
   *
   * @return The unique ID of the created game.
   */
  def createGame(): String = {
    val gameId = java.util.UUID.randomUUID().toString
    games += (gameId -> Game(gameId, List()))
    gameId
  }


  /**
   * Allows clients to join an existing game using the game ID.
   *
   * @param gameId     The ID of the game to join.
   * @param playerName The name of the player joining the game.
   * @return A message confirming the action or reporting errors.
   */
  def joinGame(gameId: String, playerName: String): String = {
    games.get(gameId) match {
      case Some(game) =>
        // Add the player to the game's player list
        val updatedPlayers = game.players :+ playerName
        val updatedGame = game.copy(players = updatedPlayers)
        games += (gameId -> updatedGame) // Save the updated game

        s"Player $playerName joined game $gameId."

      case None =>
        s"Game with ID $gameId not found." // Error if the game ID is invalid
    }
  }


  /**
   * Starts the game and distributes cards to players.
   *
   * @param gameId The ID of the game.
   * @return A message confirming the start of the game or reporting errors.
   */
  def startGame(gameId: String): String = {
    games.get(gameId) match {
      case Some(game) if game.players.nonEmpty =>
        // Distribute cards to players and the table
        dealCards(gameId)
        s"Game $gameId started with players: ${game.players.mkString(", ")}."

      case Some(_) =>
        s"Game $gameId cannot start. No players have joined yet."

      case None =>
        s"Game with ID $gameId not found." // Error if the game ID is invalid
    }
  }

  /**
   * Updates the current turn to the next player.
   *
   * @param gameId The ID of the game.
   */
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


  /**
   * Allows a previously disconnected player to rejoin an existing game.
   * This method reintegrates the player into the game's active players list,
   * removes them from the disconnected players list, and resets their timeout.
   * It ensures that the game's state is updated accordingly.
   *
   * Behavior:
   * - Checks if the player is part of the `disconnectedPlayers` list for the specified game.
   * - Updates the game's state by adding the player to the `players` list and removing them from `disconnectedPlayers`.
   * - Resets the player's timeout using `TimeoutManager.recordAction` and schedules a new timeout.
   * - Notifies other clients about the player's reconnection via WebSocket broadcasting.
   *
   * Usage:
   * Typically invoked via the `reconnectPlayer` route, allowing players who were disconnected to rejoin the game.
   *
   * @param gameId     The ID of the game the player wants to reconnect to.
   * @param playerName The name of the player attempting to reconnect.
   * @return A message confirming the reconnection or reporting any errors.
   */
  def reconnectPlayer(gameId: String, playerName: String): String = {
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
          handleTimeout(gameId, playerName)
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
   * Behavior:
   * - Removes the player from the game's `players` list.
   * - Adds the player to the game's `disconnectedPlayers` list to track disconnections.
   * - Updates the `currentTurn` to ensure the game progresses smoothly, avoiding the disconnected player.
   * - Saves the updated game state in the `games` map.
   * - Notifies other players about the disconnection using WebSocket broadcasting.
   * - Removes the player from the `TimeoutManager` to stop tracking their activity.
   *
   * Usage:
   * Typically invoked when a player disconnects manually or times out due to inactivity.
   *
   * @param gameId     The ID of the game the player is disconnecting from.
   * @param playerName The name of the player being disconnected.
   */
  def handleDisconnection(gameId: String, playerName: String): Unit = {
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
  def onPlayerAction(playerName: String): Unit = {
    TimeoutManager.recordAction(playerName)
    // Schedule a timeout for the player to monitor inactivity
    TimeoutManager.scheduleTimeout(playerName, 60000) {
      games.foreach { case (id, game) =>
        if (game.players.contains(playerName)) {
          handleTimeout(id, playerName)
        }
      }
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
  def handleTimeout(gameId: String, playerName: String): Unit = {
    log.info(s"Player $playerName timed out due to inactivity.")

    // Remove the player's WebSocket connection
    WebSocketHandler.removeConnection(games, playerName)

    // Update the game state for the specified game
    games.get(gameId) match {
      case Some(game) =>
        handleDisconnection(gameId, playerName) // Handle disconnection for this game
      case None =>
        log.warning(s"Game with ID $gameId not found when handling timeout.")
    }

    // Notify other clients about the player's disconnection
    WebSocketHandler.broadcastToOtherClients(
      TextMessage(s"Player $playerName has been disconnected due to inactivity.")
    )
  }


  /**
   * Checks if a game has been abandoned (no players remain) and removes it from the games map.
   *
   * @param gameId The ID of the game to check.
   */
  def checkGameStatus(gameId: String): Unit = {
    games.get(gameId) match {
      case Some(game) =>
        if (game.players.isEmpty) { // If no active players
          games -= gameId // Remove the game from the map
          println(s"Game $gameId has been abandoned and removed.")
        } else {
          println(s"Game $gameId is active with players: ${game.players.mkString(", ")}.")
        }
      case None =>
        println(s"Game with ID $gameId not found during cleanup.")
    }
  }

  /**
   * Finds combinations of cards on the table that can sum up to capture a played card.
   *
   * @param tableCards The list of face-up cards on the table.
   * @param playedCard The card played by the player.
   * @return A list of cards captured through summation.
   */
  def findAdditions(tableCards: List[String], playedCard: String): List[String] = {
    val playedValue = playedCard.split(" ").head.toInt // Extract the numeric value of the played card

    // Find combinations that sum up to the played card's value
    tableCards.combinations(2).find { combination =>
      combination.map(_.split(" ").head.toInt).sum == playedValue
    }.getOrElse(List()) // Return the valid combination or an empty list
  }

  /**
   * Handles a player's move during their turn.
   * The player can capture cards from the table or add a card to the table.
   *
   * @param gameId     The ID of the ongoing game.
   * @param playerName The name of the player making the move.
   * @param playedCard The card played by the player.
   * @return A message describing the outcome of the move.
   */
  def handleMove(gameId: String, playerName: String, playedCard: String): String = {
    games.get(gameId) match {
      case Some(game) =>
        if (game.players(game.currentTurn) != playerName) {
          log.warning(s"Invalid move: It's not $playerName's turn in game $gameId.")
          return s"It's not $playerName's turn." // Verify if it's the player's turn
        }

        // Validate if the player has the card being played
        if (!game.playerHands.getOrElse(playerName, List()).contains(playedCard)) {
          log.warning(s"Invalid move: $playerName does not have the card $playedCard in game $gameId.")
          return s"$playerName does not have the card $playedCard."
        }

        // Find cards on the table matching the played card
        val matchingTableCards = game.tableCards.filter(_ == playedCard)
        // Find combinations that can sum up to the played card's value
        val addableCombinations = findAdditions(game.tableCards, playedCard)

        if (matchingTableCards.nonEmpty || addableCombinations.nonEmpty) {
          val cardsToCapture = if (matchingTableCards.nonEmpty) matchingTableCards else addableCombinations
          val updatedTableCards = game.tableCards.diff(cardsToCapture) // Remove captured cards
          val updatedCaptured = game.capturedDecks.updated(
            playerName, game.capturedDecks(playerName) ++ cardsToCapture // Update the captured deck
          )

          val updatedGame = game.copy(
            tableCards = updatedTableCards,
            capturedDecks = updatedCaptured
          )
          games += (gameId -> updatedGame) // Save the updated state
          updateTurn(gameId) // Move to the next player
          return s"$playerName captured cards: ${cardsToCapture.mkString(", ")}"
        } else {
          // If no matching cards, add the played card to the table
          val updatedGame = game.copy(
            tableCards = game.tableCards :+ playedCard
          )
          games += (gameId -> updatedGame)
          updateTurn(gameId) // Move to the next player
          return s"$playerName played $playedCard onto the table."
        }

      case None =>
        s"Game with ID $gameId not found."
    }
  }


  /**
   * Distributes cards to players and places four face-up cards on the table.
   *
   * @param gameId The ID of the game for which the cards are distributed.
   */

  def dealCards(gameId: String): Unit = {
    games.get(gameId) match {
      case Some(game) =>
        // Create the Italian card deck
        val italianDeck = for {
          suit <- List("Coppe", "Denari", "Spade", "Bastoni") // Italian suits
          rank <- 1 to 10 // Numeric values from 1 to 10
        } yield s"$rank of $suit" // Card format: "number of suit"

        val shuffledDeck = scala.util.Random.shuffle(italianDeck) // Shuffle the deck

        // Distribute 3 cards to each player
        val (updatedDeckAfterPlayers, playersHands) = game.players.foldLeft((shuffledDeck, Map[String, List[String]]())) {
          case ((remainingDeck, hands), player) =>
            val hand = remainingDeck.take(3)
            val newDeck = remainingDeck.drop(3)
            (newDeck, hands + (player -> hand))
        }

        // Take 4 face-up cards for the table
        val tableCards = updatedDeckAfterPlayers.take(4)
        val updatedDeckAfterTable = updatedDeckAfterPlayers.drop(4)


        // Update the game's state
        val updatedGame = game.copy(
          playerHands = playersHands,
          tableCards = tableCards
        )
        games += (gameId -> updatedGame) // Save the updated state

      case None =>
        println(s"Game with ID $gameId not found") // Error message if the game is not found
    }
  }


  /**
   * Allows a player to steal another player's deck.
   * A deck can only be stolen if the played card matches the top card of the opponent's captured pile.
   *
   * @param gameId The ID of the ongoing game.
   * @param playerName The name of the player attempting to steal the deck.
   * @return A message describing the outcome of the action.
   */
  def stealDeck(gameId: String, playerName: String): String = {
    games.get(gameId) match {
      case Some(game) =>
        val lastCapturedCard = game.capturedDecks(playerName).lastOption // Retrieve the last captured card
        val previousPlayer = game.players((game.currentTurn - 1 + game.players.size) % game.players.size) // Previous player

        lastCapturedCard match {
          case Some(card) if game.capturedDecks(previousPlayer).lastOption.contains(card) =>
            val stolenCards = game.capturedDecks(previousPlayer)
            val updatedCaptured = game.capturedDecks.updated(
              playerName, game.capturedDecks(playerName) ++ stolenCards // Add stolen cards
            ).updated(previousPlayer, List()) // Empty the opponent's captured pile

            val updatedGame = game.copy(capturedDecks = updatedCaptured)
            games += (gameId -> updatedGame) // Save the updated state

            s"$playerName stole the deck from $previousPlayer!"
          case _ =>
            s"$playerName cannot steal the deck."
        }

      case None =>
        s"Game with ID $gameId not found."
    }
  }


  /**
   * Calculates the score and determines the winner at the end of the game.
   * The game ends when there are no more cards to distribute and on the table.
   *
   * @param gameId The ID of the ongoing game.
   * @return A message indicating the winner and their score.
   */
  def endGame(gameId: String): String = {
    games.get(gameId) match {
      case Some(game) =>
        if (game.playerHands.forall(_._2.isEmpty) && game.tableCards.isEmpty) { // Check if the game is over
          val scores = game.capturedDecks.map { case (player, cards) => player -> cards.size } // Calculate scores
          val winner = scores.maxBy(_._2)._1 // Find the player with the highest score
          games.remove(gameId)
          s"Game over! Winner is $winner with ${scores(winner)} cards!"
        } else {
          "The game is not yet over."
        }
      case None =>
        s"Game with ID $gameId not found."
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


