package server


import model.Game
import server.GameManager
import server.PlayerManager
import akka.event.Logging
import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.TextMessage
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


object MoveManager {

  private val system = ActorSystem("MoveManager")
  private val log = Logging(system, getClass)
  implicit val ec: ExecutionContext = ExecutionContext.global


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
   * Validates if the game exists in the game map.
   * @param games A mutable map storing all ongoing games.
   * @param gameId The unique identifier of the game.
   * @return Option[Game] - Returns the game if found, otherwise None.
   */
  def validateGameExists(games: scala.collection.mutable.Map[String, Game], gameId: String): Option[Game] = {
    if (!games.contains(gameId)) {
      log.warning(s"Game with ID $gameId not found.")
      return None
    }
    games.get(gameId)
  }

  /**
   * Validates if the player is active and if it's their turn to play.
   *
   * @param game          The current game instance.
   * @param playerName    The player's name.
   * @param activePlayers List of players who are still connected.
   * @return Boolean - Returns true if the player can play, otherwise false.
   */
  def validatePlayerAndTurn(game: Game, playerName: String, activePlayers: List[String]): Boolean = {
    if (!activePlayers.contains(playerName)) {
      log.warning(s"$playerName is disconnected.")
      return false
    }
    if (game.players(game.currentTurn) != playerName) {
      log.warning(s"It's not $playerName's turn.")
      return false
    }
    true
  }

  /**
   * Checks if the player has the card they intend to play.
   *
   * @param game       The current game instance.
   * @param playerName The player's name.
   * @param playedCard The card the player is trying to play.
   * @return Boolean - Returns true if the card is in the player's hand, otherwise false.
   */
  def validateCard(game: Game, playerName: String, playedCard: String): Boolean = {
    game.playerHands.getOrElse(playerName, List()).contains(playedCard)
  }

  /**
   * Handles the stealing of a deck if the player meets the conditions.
   *
   * @param game          The current game instance.
   * @param playerName    The player's name attempting to steal a deck.
   * @param playedCard    The card played that might allow deck stealing.
   * @param games         The game map storing active games.
   * @param activePlayers List of currently connected players.
   * @return String - Returns the result of the deck steal action.
   */
  def handleDeckSteal(game: Game, playerName: String, playedCard: String, games: scala.collection.mutable.Map[String, Game], activePlayers: List[String]): String = {
    val stealDeckResult = GameManager.stealDeck(game.id, playerName, playedCard)
    if (!stealDeckResult.contains("cannot steal")) {
      log.info(s"Player $playerName successfully stole the deck!")
      val updatedHandsAfterSteal = game.playerHands.updated(playerName, game.playerHands(playerName).filterNot(_ == playedCard))
      val updatedGame = game.copy(
        playerHands = updatedHandsAfterSteal,
        capturedDecks = games.getOrElse(game.id, game).capturedDecks
      )
      //games += (game.id -> updatedGame)
      val updatedTurnCompleted = updatedGame.turnCompleted.updated(playerName, true)
      val updatedGameWithTurn = updatedGame.copy(turnCompleted = updatedTurnCompleted)
      games += (game.id -> updatedGameWithTurn)

      GameManager.updateTurn(game.id)
      val allPlayersOutOfCardsAfterSteal = activePlayers.forall(player => game.playerHands.getOrElse(player, List()).isEmpty)
      if (allPlayersOutOfCardsAfterSteal && game.deck.nonEmpty) {
        log.info("After the theft, all players have run out of cards. Redistribution in progress...")
        val (newHands, remainingDeck) = activePlayers.foldLeft((Map[String, List[String]](), game.deck)) {
          case ((hands, deck), player) =>
            val (newHand, updatedDeck) = deck.splitAt(game.startingHandSize)
            (hands + (player -> newHand), updatedDeck)
        }
        games += (game.id -> game.copy(
          deck = remainingDeck,
          playerHands = newHands
        ))

      }
      //GameManager.updateTurn(game.id)
      return stealDeckResult
    }
    ""
  }

  /**
   * Handles the capture of cards from the table based on the player's move.
   *
   * @param game       The current game instance.
   * @param playerName The player's name.
   * @param playedCard The card played that may capture other cards.
   * @return Game - Returns the updated game state after capturing cards.
   */
  def captureCards(game: Game, playerName: String, playedCard: String): Game = {
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

    val updatedPlayerHands = game.playerHands.updated(playerName, game.playerHands.getOrElse(playerName, List()).filterNot(_ == playedCard))
    log.info(s"Updated player hands for $playerName: ${updatedPlayerHands.getOrElse(playerName, List()).mkString(", ")}")

    game.copy(
      tableCards = updatedTableCards,
      capturedDecks = updatedCapturedDecks,
      playerHands = updatedPlayerHands
    )
  }

  /**
   * Checks if the game needs redistribution of cards or should end.
   *
   * @param games         The game map storing active games.
   * @param game          The current game instance.
   * @param gameId        The unique identifier of the game.
   * @param activePlayers List of currently connected players.
   * @return Game - Returns the updated game state after checking conditions.
   */
  def checkRedistributionOrGameEnd(games: scala.collection.mutable.Map[String, Game], game: Game, gameId: String, activePlayers: List[String]): Game = {
    // Store the current hands, decks, and table cards
    val updatedHands = game.playerHands
    var updatedCapturedDecks = game.capturedDecks
    var updatedDeck = game.deck
    var newTableCards = game.tableCards
    var newPlayerHands = updatedHands

    val allPlayersOutOfCards = activePlayers.forall(player => updatedHands.getOrElse(player, List()).isEmpty)
    val tableIsEmpty = newTableCards.isEmpty
    val deckHasCards = game.deck.nonEmpty
    // Identify the player with the highest number of captured decks
    val topScoringPlayers = game.capturedDecks.toList.sortBy(-_._2.size)
    val bestPlayerOpt = topScoringPlayers.headOption.map(_._1)

    // Handle table card refill if the table is empty and the deck has cards
    if (tableIsEmpty && deckHasCards) {
      if (game.deck.size < 4) {
        log.info(s"Not enough cards in the deck to refill the table. Assigning remaining deck cards to top player: $bestPlayerOpt")
        bestPlayerOpt.foreach { bestPlayer =>
          updatedCapturedDecks = game.capturedDecks.updated(
            bestPlayer, game.capturedDecks.getOrElse(bestPlayer, List()) ++ game.deck
          )
        }
        updatedDeck = List()
        newTableCards = List()
      } else {
        log.info("The table is empty, refilling with new cards...")
        val (newCardsForTable, newRemainingDeck) = game.deck.splitAt(4)
        log.info(s"New table cards: ${newCardsForTable.mkString(", ")}")
        newTableCards = newCardsForTable
        updatedDeck = newRemainingDeck
      }
    }

    // Handle player hand redistribution if all players have no cards
    if (allPlayersOutOfCards && deckHasCards) {
      if (game.deck.size < game.players.size * game.startingHandSize) {
        log.info(s"Not enough cards in the deck to refill player hands. Assigning remaining deck cards to top player: $bestPlayerOpt")
        bestPlayerOpt.foreach { bestPlayer =>
          updatedCapturedDecks = game.capturedDecks.updated(
            bestPlayer, game.capturedDecks.getOrElse(bestPlayer, List()) ++ game.deck
          )
        }
        updatedDeck = List()
        newPlayerHands = game.players.map(player => player -> List()).toMap
      } else {
        log.info("All players are out of cards, redistributing new hands...")
        val (newHands, remainingDeck) = activePlayers.foldLeft((Map[String, List[String]](), updatedDeck)) {
          case ((hands, deck), player) =>
            val (newHand, updatedDeck) = deck.splitAt(game.startingHandSize)
            (hands + (player -> newHand), updatedDeck)
        }
        newPlayerHands = newHands
        updatedDeck = remainingDeck
      }
    }

    // Update the game state with new values
    val updatedGame = game.copy(
      playerHands = newPlayerHands,
      tableCards = newTableCards,
      capturedDecks = updatedCapturedDecks,
      deck = updatedDeck
    )
    // Store the updated game in the active games map
    games += (gameId -> updatedGame)

    GameManager.updateTurn(gameId)

    // Check if the game should end
    if (updatedGame.deck.isEmpty && newTableCards.isEmpty && allPlayersOutOfCards) {
      log.info("The deck, table cards and player hands are empty. Game is ending. Finalizing results...")
      WebSocketHandler.broadcastToOtherClients(
        "Server",
        TextMessage(s"Game $gameId is ending. Finalizing results..."))
      Future {
        Thread.sleep(10000) // Delay before finalizing the game
        GameManager.endGame(gameId)
      }(ExecutionContext.global)
    }
    updatedGame
  }

  /**
   * Handles the player's move in the game, including validation, card playing, deck stealing,
   * and game state updates.
   *
   * @param games       A mutable map storing all ongoing games.
   * @param gameId      The unique identifier of the game.
   * @param playerName  The name of the player making the move.
   * @param playedCard  The card the player intends to play.
   * @return String -   A message confirming the move and game state update.
   */
  def handleMove(games: scala.collection.mutable.Map[String, Game], gameId: String, playerName: String, playedCard: String): String = {

    // Validate if the game exists
    val gameOpt = validateGameExists(games, gameId)
    if (gameOpt.isEmpty) return s"Game with ID $gameId not found."
    val game = gameOpt.get
    // Get the list of active players (excluding disconnected ones)
    val activePlayers = game.players.filterNot(game.disconnectedPlayers.contains)

    //  Validate if it's the player's turn and they are still in the game
    if (!validatePlayerAndTurn(game, playerName, activePlayers)) return s"Invalid move for $playerName because it is not his/her turn."

    // Validate if the player has the card they are trying to play
    if (!validateCard(game, playerName, playedCard)) return s"$playerName does not have the card $playedCard."

    // Handle deck stealing (if applicable)
    val stealDeckResult = handleDeckSteal(game, playerName, playedCard, games, activePlayers)
    if (stealDeckResult.nonEmpty) return stealDeckResult

    // Process card capturing logic
    val updatedGame = captureCards(game, playerName, playedCard)

    val updatedTurnCompleted = updatedGame.turnCompleted.updated(playerName, true)
    /*val updatedTurnCompleted = if (!game.disconnectedPlayers.contains(playerName)) {
      updatedGame.turnCompleted.updated(playerName, true)
    } else {
      updatedGame.turnCompleted
    }*/
    val updatedGameWithTurn = updatedGame.copy(turnCompleted = updatedTurnCompleted)
    games += (gameId -> updatedGameWithTurn)

    // Check whether redistribution or game ending conditions apply
    val finalGameState = checkRedistributionOrGameEnd(games, updatedGameWithTurn, gameId, activePlayers)

    games += (gameId -> finalGameState)
    GameManager.updateTurn(gameId)

    return s"$playerName played $playedCard and the game state has been updated."
  }



}

