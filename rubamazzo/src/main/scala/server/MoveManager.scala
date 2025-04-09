package server


import model.Game
import server.GameManager
import server.PlayerManager
import akka.event.Logging
import akka.actor.ActorSystem

object MoveManager {

  private val system = ActorSystem("MoveManager")
  private val log = Logging(system, getClass)

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
  def handleMove(games: scala.collection.mutable.Map[String, Game], gameId: String, playerName: String, playedCard: String): String = {
    games.get(gameId) match {
      case Some(game) =>
        log.info(s"Handling move for player: $playerName in game $gameId. Played card: $playedCard")

        if (game.players(game.currentTurn) != playerName) {
          log.warning(s"Invalid move: It's not $playerName's turn in game $gameId.")
          return s"It's not $playerName's turn." // Verify if it's the player's turn
        }

        // Validate if the player has the card being played
        if (!game.playerHands.getOrElse(playerName, List()).contains(playedCard)) {
          log.warning(s"Invalid move: $playerName does not have the card $playedCard in game $gameId.")
          return s"$playerName does not have the card $playedCard."
        }

        // Remove played card from player's hands
        val updatedPlayerHand = game.playerHands(playerName).filterNot(_ == playedCard)
        val updatedHands = game.playerHands.updated(playerName, updatedPlayerHand)
        log.info(s"Updated player hands: ${updatedHands(playerName).mkString(", ")}")


        // Extract the numerical value of the played card
        val playedValue = playedCard.split(" ").head.toInt
        log.info(s"Extracted played card value: $playedValue")


        val updatedCapturedDecks: Map[String, List[String]]= game.capturedDecks.updated(
          playerName, game.capturedDecks.getOrElse(playerName, List())
        )
        log.info(s"Captured decks before move: ${updatedCapturedDecks.mkString(", ")}")

        log.info(s"Current table cards: ${game.tableCards.mkString(", ")}")

        // Check if the player can steal the deck
        val stealDeckResult = GameManager.stealDeck(gameId, playerName)
        log.info(s"Steal deck result: $stealDeckResult")
        if (!stealDeckResult.contains("cannot steal")) { // Successful steal
          log.info(s"Player $playerName successfully stole the deck!")
          val updatedGame = game.copy(
            playerHands = updatedHands,
            capturedDecks = updatedCapturedDecks
          ) // Update player hands after stealing
          games += (gameId -> updatedGame)
          GameManager.updateTurn(gameId) // Update turn after stealing
          return stealDeckResult
        }


        // Find cards on the table matching the played value
        val matchingTableCards: List[String] = game.tableCards.filter { card =>
            card.split(" ").head.toInt == playedValue
        }
        log.info(s"Matching cards on the table: ${matchingTableCards.mkString(", ")}")
        // Find combinations that can sum up to the played card's value
        val addableCombinations: List[String] = findAdditions(game.tableCards, playedCard).map(_.toString)
        log.info(s"Addable combinations found: ${addableCombinations.mkString(", ")}")


        if (matchingTableCards.nonEmpty || addableCombinations.nonEmpty) {
          val cardsToCapture: List[String] = if (matchingTableCards.nonEmpty) {
            matchingTableCards
          } else {
            addableCombinations
          }
          log.info(s"Cards captured by $playerName: ${cardsToCapture.mkString(", ")}")

          val updatedTableCards = game.tableCards.diff(cardsToCapture) // Remove captured cards
          log.info(s"Updated table cards after capture: ${updatedTableCards.mkString(", ")}")

          val updatedCaptured = updatedCapturedDecks.updated(
            playerName, (playedCard +: updatedCapturedDecks.getOrElse(playerName, List())) ++ cardsToCapture
          )
          log.info(s"Updated captured decks for $playerName: ${updatedCaptured(playerName).mkString(", ")}")

          log.info(s"Type of updatedCapturedDecks: ${updatedCapturedDecks.getClass}")
          log.info(s"Contents of updatedCapturedDecks: ${updatedCapturedDecks.mkString(", ")}")


          val updatedGame = game.copy(
            playerHands = updatedHands,
            tableCards = updatedTableCards,
            capturedDecks = updatedCaptured
          )
          games += (gameId -> updatedGame) // Save the updated state
         GameManager.updateTurn(gameId) // Move to the next player
          return s"$playerName captured cards: ${cardsToCapture.mkString(", ")}"
        } else {
          // If no matching cards, add the played card to the table
          log.info(s"No cards captured. Adding $playedCard to the table.")
          val updatedGame = game.copy(
              playerHands = updatedHands,
              tableCards = game.tableCards :+ playedCard,
              capturedDecks = updatedCapturedDecks
          )
          games += (gameId -> updatedGame)
          GameManager.updateTurn(gameId) // Move to the next player
          return s"$playerName played $playedCard onto the table."
        }

      case None =>
        log.error(s"Game with ID $gameId not found.")
        s"Game with ID $gameId not found."
    }
  }

}

