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

    val playedType = playedCard.split(" ").head
    val playedValue = playedType match {
      case "Fante" | "Cavallo" | "Re" => -1
      case num if num.forall(_.isDigit) => num.toInt
      case _ => -1
    }
    // Find combinations that sum up to the played card's value
    tableCards.combinations(2).find { combination =>
      combination.map(_.split(" ").head).forall(_.forall(_.isDigit)) &&
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


        val updatedPlayerHand = game.playerHands(playerName).filterNot(_ == playedCard)
        val updatedHands = game.playerHands.updated(playerName, updatedPlayerHand)
        log.info(s"Updated player hands: ${updatedHands(playerName).mkString(", ")}")



        log.info(s"Current table cards: ${game.tableCards.mkString(", ")}")
        log.info(s"Captured decks before move: ${game.capturedDecks.mkString(", ")}")

        // Check if the player can steal the deck
        val stealDeckResult = GameManager.stealDeck(gameId, playerName, playedCard)
        if (!stealDeckResult.contains("cannot steal")) {
          log.info(s"Player $playerName successfully stole the deck!")
          val updatedHandsAfterSteal = game.playerHands.updated(playerName, updatedHands(playerName).filterNot(_ == playedCard))
          val updatedGame = game.copy(
            playerHands = updatedHandsAfterSteal,
            capturedDecks = games(gameId).capturedDecks)
          games += (gameId -> updatedGame)

          GameManager.updateTurn(gameId)
          return stealDeckResult
        }

        val playedType = playedCard.split(" ").head
        log.info(s"Extracted played card type: $playedType")

        val matchingTableCards = game.tableCards.filter(card => card.split(" ").head == playedType)
        val addableCombinations = findAdditions(game.tableCards, playedCard).filter(_.split(" ").head.forall(_.isDigit))

        var updatedTableCards = game.tableCards
        var updatedCapturedDecks = game.capturedDecks

        if (matchingTableCards.nonEmpty || addableCombinations.nonEmpty) {
          val cardsToCapture = matchingTableCards ++ addableCombinations
          log.info(s"Cards captured by $playerName: ${cardsToCapture.mkString(", ")}")

          updatedTableCards = game.tableCards.diff(cardsToCapture)
          updatedCapturedDecks = game.capturedDecks.updated(
            playerName, List(playedCard) ++ game.capturedDecks.getOrElse(playerName, List()) ++ cardsToCapture
          )
        } else {
          log.info(s"No cards captured. Adding $playedCard to the table.")
          updatedTableCards = game.tableCards :+ playedCard
        }

        // Redistribute cards only if needed, after captures
        val allPlayersOutOfCards = game.players.forall(player => updatedHands.getOrElse(player, List()).isEmpty)
        val tableIsEmpty = updatedTableCards.isEmpty
        val deckHasCards = game.deck.nonEmpty

        var updatedDeck = game.deck
        var newTableCards = updatedTableCards
        var newPlayerHands = updatedHands


        if (tableIsEmpty && deckHasCards) {
          log.info("The table is empty, refilling with new cards...")

          val (newCardsForTable, newRemainingDeck) = game.deck.splitAt(4)
          log.info(s"New table cards: ${newCardsForTable.mkString(", ")}")

          newTableCards = newCardsForTable
          updatedDeck = newRemainingDeck
        }


        if (allPlayersOutOfCards && deckHasCards) {
          log.info("All players are out of cards, redistributing new hands...")

          val (newHands, remainingDeck) = game.players.foldLeft((Map[String, List[String]](), updatedDeck)) {
            case ((hands, deck), player) =>
              val (newHand, updatedDeck) = deck.splitAt(game.startingHandSize)
              (hands + (player -> newHand), updatedDeck)
          }

          newPlayerHands = newHands
          updatedDeck = remainingDeck
        }

        val updatedGame = game.copy(
          playerHands = newPlayerHands,
          tableCards = newTableCards,
          capturedDecks = updatedCapturedDecks,
          deck = updatedDeck
        )

        games += (gameId -> updatedGame)
        GameManager.updateTurn(gameId)

        return s"$playerName played $playedCard and the game state has been updated."
      case None =>
        log.error(s"Game with ID $gameId not found.")
        return s"Game with ID $gameId not found."
    }
  }

}

