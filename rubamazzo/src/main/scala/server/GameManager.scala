package server

import akka.http.scaladsl.model.ws.TextMessage
import model.Game
import server.PlayerManager
import akka.http.scaladsl.model.StatusCodes
import akka.actor.ActorSystem
import akka.event.Logging
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


object GameManager {


  private val system = ActorSystem("GameManager")
  private val log = Logging(system, getClass)
  implicit val ec: ExecutionContext = ExecutionContext.global
  var originalPlayerOrders: scala.collection.mutable.Map[String, Map[String, Int]] = scala.collection.mutable.Map()

  var games: scala.collection.mutable.Map[String, Game] = scala.collection.mutable.Map()



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
   * Allows a player to join a game with the specified gameId.
   *
   * @param gameId The ID of the game the player wants to join.
   * @param playerName The name of the player who wants to join the game.
   * @return A string message indicating the result of the operation:
   *         - Success message if the player joined the game successfully.
   *         - Warning message if the player is already in the game.
   *         - Error message if the game does not exist.
   */
  def joinGame(gameId: String, playerName: String): String = {
    games.get(gameId) match {
      case Some(game) =>
        log.info(s"Before join: Players in game $gameId: ${game.players}")
        // Check if the player is already part of the game
        if (game.players.contains(playerName)) {
          log.warning(s"Player $playerName is already in game $gameId")
          s"Player $playerName is already part of the game"
        } else {
          // Add the player to the game's player list
          val updatedPlayers = game.players :+ playerName
          val updatedGame = game.copy(players = updatedPlayers)
          games += (gameId -> updatedGame)
          log.info(s"After join: Players in game $gameId: ${updatedGame.players}")

          s"Player $playerName joined game $gameId."
        }

      case None =>
        log.error(s"Game with ID $gameId not found when attempting to join")
        s"Game with ID $gameId not found." // Error if the game ID is invalid
    }
  }

  /**
   * Starts the specified game if players have joined.
   *
   * @param gameId The unique identifier for the game to start.
   * @return A string message indicating the result:
   *         - Success message if the game starts successfully.
   *         - Warning message if there are no players in the game.
   *         - Error message if the game ID is invalid.
   */
  def startGame(gameId: String): String = {
    games.get(gameId) match {
      // Game exists and players have joined
      case Some(game) if game.players.nonEmpty =>
        originalPlayerOrders(gameId) = game.players.zipWithIndex.toMap

        // Distribute cards to players and the table
        dealCards(gameId)
        log.info(s"Game $gameId started with players: ${game.players.mkString(", ")}")
        s"Game $gameId started with players: ${game.players.mkString(", ")}."

      // Game exists but no players have joined
      case Some(_) =>
        log.warning(s"Game $gameId cannot start because no players have joined.")
        s"Game $gameId cannot start. No players have joined yet."

      // Game does not exist
      case None =>
        log.error(s"Game with ID $gameId not found when starting the game.")
        s"Game with ID $gameId not found when starting the game."
    }
  }


  /**
   * Updates the turn for the specified game, ensuring the next player is correctly set.
   * The turn will advance if the current player has completed their move or is disconnected.
   * If all players are disconnected, the game pauses and waits for reconnections.
   * If all hands and the deck are empty, the game ends.
   *
   * @param gameId The unique identifier for the game whose turn is being updated.
   * @return A string message indicating the result of the operation:
   *         - Success message with details of the next player if the turn is updated successfully.
   *         - Warning message if there are no players in the game.
   *         - Notification message if waiting for player reconnections.
   *         - Error message if the game does not exist.
   *
   * Functionality:
   *         - Retrieves the game instance if it exists.
   *         - Determines active players, excluding disconnected ones.
   *         - Checks if all players are disconnected, in which case the game waits.
   *         - Skips disconnected players when assigning the next turn.
   *         - If the current player has completed their turn, advances to the next.
   *         - Ends the game if no cards remain in play.
   */

  def updateTurn(gameId: String): String = {
    games.get(gameId) match {
      // Game exists but has no players
      case Some(game) if game.players.isEmpty =>
        log.warning(s"[Turn Update] Game $gameId has no players remaining.")
        return "No players in the game"
      // Game exists and players are present
      case Some(game) =>
        log.info(s"[Turn Update] Game $gameId found. Players: ${game.players}, Current turn: ${game.currentTurn}")
        val activePlayers = game.players.filterNot(game.disconnectedPlayers.contains)
        var currentPlayer = game.players(game.currentTurn)

        log.info(s"[Turn Update] Current Turn Index: ${game.currentTurn}, Total Players: ${game.players.size}")

        // If all players are disconnected, the game waits for reconnections
        if (activePlayers.isEmpty) {
          log.warning(s"[Turn Update] No active players in game $gameId. Waiting for reconnection...")
          return "Waiting for players to reconnect before updating turn."
        }
        var nextTurn = game.currentTurn
        // If the current player is disconnected, update the turn to the next available player
        if (game.disconnectedPlayers.contains(currentPlayer)) {
          log.info(s"[Turn Update] $currentPlayer has disconnected, selecting next turn...")
          do {
            nextTurn = (nextTurn + 1) % game.players.size
          } while ((game.players.lift(nextTurn).exists(game.disconnectedPlayers.contains))) // Skip disconnected players
          log.info(s"[Turn Update] New turn assigned to: ${game.players(nextTurn)}.")
          val updatedGame = game.copy(currentTurn = nextTurn)
          games += (gameId -> updatedGame)
          return s"Turn updated for game $gameId. Next turn: Player ${updatedGame.players(nextTurn)}."
        }


        // If the current player has not completed their turn, keep it unchanged
        if (!game.turnCompleted.getOrElse(currentPlayer, false)) {
          log.info(s"[Turn Update] No changes: Current player has not completed their turn.")
          return s"Turn remains unchanged for game $gameId. Current turn: Player ${game.players(game.currentTurn)}."
        }

        // If the game has to end, check the status of the cards
        if (game.playerHands.forall(_._2.isEmpty) && game.deck.isEmpty) {
          log.info(s"[Game End Check] All players have no cards left and the deck is empty. Ending game $gameId.")
          return GameManager.endGame(gameId)
        }
        // Normal turn progression: Move to the next player ONLY if the current player has completed their move
        log.info(s"[Turn Update] Advancing turn after player action...")
        do {
          nextTurn = (nextTurn + 1) % game.players.size
        } while ((game.players.lift(nextTurn).exists(game.disconnectedPlayers.contains))) // Skip disconnected players

        log.info(s"[Turn Update] New turn assigned to: ${game.players(nextTurn)}.")

        val resetTurnCompleted = game.turnCompleted.updated(game.players(nextTurn), false)
        val updatedGame = game.copy(currentTurn = nextTurn, turnCompleted = resetTurnCompleted)
        games += (gameId -> updatedGame)
        return s"Turn updated for game $gameId. Next turn: Player ${updatedGame.players(nextTurn)}."
      // Game does not exist
      case None =>
        log.warning(s"Game with ID $gameId not found")
        return s"Game with ID $gameId not found."
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
          suit <- List("Coppe", "Denari", "Spade", "Bastoni")
          rank <- (1 to 10).map(_.toString) ++ List("Fante", "Cavallo", "Re")
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
          tableCards = tableCards,
          deck = updatedDeckAfterTable
        )
        games += (gameId -> updatedGame) // Save the updated state

        log.info("New game setup complete. Deck shuffled and cards distributed.")

      case None =>
        println(s"Game with ID $gameId not found") // Error message if the game is not found
    }
  }


  /**
   * Allows a player to steal another player's deck.
   * A deck can only be stolen if the played card matches the top card of the opponent's captured pile.
   * If a successful steal occurs, the stolen deck is added to the player's captured decks,
   * and the opponent's captured deck is emptied.
   *
   * @param gameId     The ID of the ongoing game.
   * @param playerName The name of the player attempting to steal the deck.
   * @param playedCard The card played by the player.
   * @return A message describing the outcome of the action.
   *
   *  Functionality:
   * - Verifies that the game exists and retrieves its state.
   * - Identifies a stealable player whose last captured card matches the played card.
   * - Transfers the stolen deck to the player attempting the steal.
   * - Clears the stolen deck from the target player's captured pile.
   * - Updates the game state accordingly and logs all relevant actions.
   * - If no valid steal is found, logs and returns an appropriate message.
   * - Handles the case where the game ID is invalid or not found.
   */

  def stealDeck(gameId: String, playerName: String, playedCard: String): String = {
    games.get(gameId) match {
      case Some(game) =>
        log.info(s"$playerName attempts to steal a deck using $playedCard.")


        // Find a player you can steal the deck from by comparing the card played with their last captured card
        val stealablePlayer = game.players.find { otherPlayer =>
          otherPlayer != playerName &&
            game.capturedDecks.getOrElse(otherPlayer, List()).nonEmpty &&
            game.capturedDecks(otherPlayer).headOption.exists { firstCaptured =>
              firstCaptured.split(" ").head == playedCard.split(" ").head
          }
        }

        stealablePlayer match {
          case Some(targetPlayer) =>
            val stolenCards = game.capturedDecks.getOrElse(targetPlayer, List())
            log.info(s"Before transfer: $playerName capturedDecks=${game.capturedDecks.getOrElse(playerName, List())}")
            log.info(s"Stealing from $targetPlayer: deck=${stolenCards.mkString(", ")}")

            val newCapturedDeck = List(playedCard) ++ stolenCards ++ game.capturedDecks.getOrElse(playerName, List())

            // Update captured decks
            val updatedCapturedDecks = game.capturedDecks
              .updated(playerName, newCapturedDeck)
              .updated(targetPlayer, List())
            val updatedGame = game.copy(
              capturedDecks = updatedCapturedDecks
            )
            games += (gameId -> updatedGame)

            log.info(s"After transfer: $playerName capturedDecks=${updatedCapturedDecks.getOrElse(playerName, List())}")
            log.info(s"$targetPlayer deck emptied!")
            log.info(s"$playerName stole the deck from $targetPlayer!")
            return s"$playerName stole the deck from $targetPlayer!"

          case None =>
            log.info(s"No deck was stolen. No players had a matching last captured card for $playerName.")
            return s"$playerName cannot steal the deck."
        }
      case None =>
        log.error(s"Game with ID $gameId not found.")
        s"Game with ID $gameId not found."
    }
  }


  /**
   * Calculates the score and determines the winner at the end of the game.
   * The game ends when there are no  more cards left to distribute or play.
   * After determining the winner, the game instance is removed from active games.
   *
   * Usage:
   * Typically called when the game reaches its natural end state, ensuring that final scores are computed.
   *
   * @param gameId The ID of the ongoing game.
   * @return A message indicating the winner and their score or notifying if no winner was determined.
   *
   * Functionality:
   * - Retrieves the game instance from active games.
   * - Computes final scores based on the number of captured cards per player.
   * - Determines the player with the highest score as the winner.
   * - Logs all final score calculations and winner determination.
   * - If no winner is found, logs a warning and reports the scores.
   * - Waits for 120 seconds before removing the game to prevent concurrent access issues.
   * - Ensures the game is properly removed from the active list after completion.
   */

  def endGame(gameId: String): String = {
    games.get(gameId) match {
      case Some(game) =>
          log.info(s"Ending game: $gameId. Calculating final scores...")
          val scores = game.capturedDecks.map { case (player, cards) => player -> cards.size }
          val winnerOpt = scores.maxByOption(_._2).map(_._1)
          val scoreMessage = scores.map { case (player, count) => s"$player: $count cards" }.mkString("\n")
          log.info(s"Final scores computed:\n$scoreMessage")
          val finalMessage = winnerOpt match {
            case Some(winner) =>
              log.info(s"Winner determined: $winner with ${scores(winner)} cards.")
              s"Game over! Winner is $winner with ${scores(winner)} cards!\n\nFinal Scores:\n$scoreMessage"
            case None =>
              log.warning("No winner could be determined.")
              "Game over! No winner could be determined.\n\nFinal Scores:\n$scoreMessage"
          }

        val updatedGame = game.copy(gameOver = true, winner = winnerOpt)
        games.update(gameId, updatedGame)
        log.info(s"[endGame] Updated game ->: ${updatedGame.toString}")


          Future {
            log.info(s"Waiting 120 seconds before removing game $gameId to prevent concurrent access issues...")
            Thread.sleep(120000)
            games.remove(gameId)
            log.info(s"Game $gameId removed from active games.")
          }(ec)
          finalMessage

      case None =>
        log.warning(s"Attempted to end game $gameId, but it was not found.")
        s"Game with ID $gameId not found. The game might have already ended."
    }
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




}



