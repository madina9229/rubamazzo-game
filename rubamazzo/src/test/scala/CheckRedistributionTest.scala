package scala

import model.Game
import server.GameManager
import server.PlayerManager
import server.MoveManager
import server.MoveManager._
import scala.collection.immutable.Map
import org.scalatest.funsuite.AnyFunSuite



class CheckRedistributionTest extends AnyFunSuite {

  val players = List("Giovanni", "Marco")
  val sampleGame = Game(
    id = "game1",
    players = players,
    currentTurn = 0,
    playerHands = Map("Giovanni" -> List(), "Marco" -> List()),
    tableCards = List(),
    capturedDecks = Map("Giovanni" -> List("1 Denari", "1 Spade", "5 Coppe", "5 Spade"), "Marco" -> List("2 Coppe", "2 Spade")),
    deck = List("3 Bastoni", "4 Denari", "6 Coppe"),
    disconnectedPlayers = List(),
    startingHandSize = 3,
    turnCompleted = Map().withDefaultValue(false)
  )

  test("checkRedistributionOrGameEnd should assign remaining deck to best player if deck is insufficient") {
    val games = scala.collection.mutable.Map("game1" -> sampleGame)
    val updatedGame = MoveManager.checkRedistributionOrGameEnd(games, sampleGame, "game1", players)

    val bestPlayer = updatedGame.capturedDecks.keys.headOption.getOrElse("")
    assert(updatedGame.capturedDecks(bestPlayer).nonEmpty, s"Best player $bestPlayer should have received the deck")
    assert(updatedGame.deck.isEmpty, "Deck should be empty after assignment")
    assert(updatedGame.capturedDecks("Giovanni").contains("1 Denari"))
    assert(updatedGame.capturedDecks("Giovanni").contains("1 Spade"))
    assert(updatedGame.capturedDecks("Giovanni").contains("5 Coppe"))
    assert(updatedGame.capturedDecks("Giovanni").contains("5 Spade"))
    assert(updatedGame.capturedDecks("Giovanni").contains("3 Bastoni"))
    assert(updatedGame.capturedDecks("Giovanni").contains("4 Denari"))
    assert(updatedGame.capturedDecks("Giovanni").contains("6 Coppe"))
  }

  test("checkRedistributionOrGameEnd should refill hands if deck has enough cards") {
    val gameWithLargeDeck = sampleGame.copy(deck = List.fill(10)("7 Coppe"))
    val games = scala.collection.mutable.Map("game1" -> gameWithLargeDeck)
    val updatedGame = MoveManager.checkRedistributionOrGameEnd(games, gameWithLargeDeck, "game1", players)

    assert(updatedGame.playerHands("Giovanni").size == gameWithLargeDeck.startingHandSize, "Giovanni should have a new hand")
    assert(updatedGame.playerHands("Marco").size == gameWithLargeDeck.startingHandSize, "Marco should have a new hand")
    assert(updatedGame.deck.size < gameWithLargeDeck.deck.size, "Deck should decrease after redistribution")
  }

  test("checkRedistributionOrGameEnd should trigger game end when deck and table are empty") {
    val emptyGame = sampleGame.copy(deck = List(), tableCards = List())
    val games = scala.collection.mutable.Map("game1" -> emptyGame)
    val updatedGame = MoveManager.checkRedistributionOrGameEnd(games, emptyGame, "game1", players)

    assert(updatedGame.deck.isEmpty, "Deck should be empty")
    assert(updatedGame.tableCards.isEmpty, "Table should be empty")
    assert(updatedGame.playerHands.values.forall(_.isEmpty), "All players should have empty hands")
  }





  test("Deck is assigned to best player when table is empty and deck has fewer than 6 cards") {
    val sampleGame = Game(
      id = "game2",
      players = players,
      currentTurn = 0,
      playerHands = Map("Giovanni" -> List("5 Denari"), "Marco" -> List("4 Coppe")),
      tableCards = List(),
      capturedDecks = Map("Giovanni" -> List("6 Spade", "6 Bastoni"), "Marco" -> List()),
      deck = List("2 Bastoni", "3 Denari", "10 Spade"),
      disconnectedPlayers = List(),
      startingHandSize = 3,
      turnCompleted = Map().withDefaultValue(false)
    )

    val games = scala.collection.mutable.Map("game2" -> sampleGame)
    val updatedGame = MoveManager.checkRedistributionOrGameEnd(games, sampleGame, "game2", players)

    val bestPlayer = updatedGame.capturedDecks.keys.headOption.getOrElse("")
    assert(updatedGame.capturedDecks(bestPlayer).size > sampleGame.capturedDecks(bestPlayer).size,
      s"Best player $bestPlayer should receive remaining deck cards")
    assert(updatedGame.deck.isEmpty, "Deck should be empty after assignment")
    assert(updatedGame.tableCards.isEmpty, "Table should remain empty")
  }

  test("Table is refilled with 4 cards when deck has enough cards") {
    val sampleGame = Game(
      id = "game2",
      players = players,
      currentTurn = 0,
      playerHands = Map("Giovanni" -> List("5 Denari"), "Marco" -> List("4 Coppe")),
      tableCards = List(),
      capturedDecks = Map("Giovanni" -> List("6 Spade", "Re Bastoni"), "Marco" -> List("7 Coppe")),
      deck = List("1 Denari", "2 Bastoni", "3 Spade", "4 Coppe", "5 Denari", "6 Spade"),
      disconnectedPlayers = List(),
      startingHandSize = 3,
      turnCompleted = Map().withDefaultValue(false)
    )

    val games = scala.collection.mutable.Map("game2" -> sampleGame)
    val updatedGame = MoveManager.checkRedistributionOrGameEnd(games, sampleGame, "game2", players)

    assert(updatedGame.tableCards.size == 4, "Table should be refilled with 4 cards")
    assert(updatedGame.deck.size < sampleGame.deck.size, "Deck should decrease after refilling the table")
  }

  test("Game ends when deck and table are empty") {
    val emptyGame = sampleGame.copy(deck = List(), tableCards = List())
    val games = scala.collection.mutable.Map("game2" -> emptyGame)
    val updatedGame = MoveManager.checkRedistributionOrGameEnd(games, emptyGame, "game2", players)

    assert(updatedGame.deck.isEmpty, "Deck should be empty")
    assert(updatedGame.tableCards.isEmpty, "Table should be empty")
    assert(updatedGame.playerHands.values.forall(_.isEmpty), "All players should have empty hands")
  }




}
