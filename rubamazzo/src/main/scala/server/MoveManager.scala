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
         GameManager.updateTurn(gameId) // Move to the next player
          return s"$playerName captured cards: ${cardsToCapture.mkString(", ")}"
        } else {
          // If no matching cards, add the played card to the table
          val updatedGame = game.copy(
            tableCards = game.tableCards :+ playedCard
          )
          games += (gameId -> updatedGame)
          GameManager.updateTurn(gameId) // Move to the next player
          return s"$playerName played $playedCard onto the table."
        }

      case None =>
        s"Game with ID $gameId not found."
    }
  }

}

