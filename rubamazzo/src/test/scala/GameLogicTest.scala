

import model.Game
import server.GameManager
import server.PlayerManager
import server.MoveManager
import server.MoveManager._
import org.scalatest.funsuite.AnyFunSuite
import scala.collection.immutable.Map


class GameLogicTest extends AnyFunSuite {


  val players = List("Giovanni", "Marco")
  val sampleGame = Game(
    id = "game1",
    players = players,
    currentTurn = 0,
    playerHands = Map("Giovanni" -> List("7 Denari", "Re Bastoni"), "Marco" -> List("5 Coppe", "1 Spade")),
    tableCards = List("7 Bastoni", "Fante Coppe"),
    capturedDecks = Map("Giovanni" -> List[String](), "Marco" -> List[String]()),
    deck = List("10 Spade", "Fante Denari", "2 Bastoni"),
    disconnectedPlayers = List(),
    startingHandSize = 2
  )

  test("validateGameExists should return Some(game) if game exists") {
    val games = scala.collection.mutable.Map("game1" -> sampleGame)
    assert(MoveManager.validateGameExists(games, "game1").nonEmpty)
  }

  test("validateGameExists should return None if game does not exist") {
    val games = scala.collection.mutable.Map("game1" -> sampleGame)
    assert(MoveManager.validateGameExists(games, "game2").isEmpty)
  }

  test("validatePlayerAndTurn should return true if it's the player's turn") {
    assert(MoveManager.validatePlayerAndTurn(sampleGame, "Giovanni", players))
  }

  test("validatePlayerAndTurn should return false if not player's turn") {
    assert(!MoveManager.validatePlayerAndTurn(sampleGame, "Marco", players))
  }

  test("validateCard should return true if player has the card") {
    assert(MoveManager.validateCard(sampleGame, "Giovanni", "7 Denari"))
  }

  test("validateCard should return false if player does not have the card") {
    assert(!MoveManager.validateCard(sampleGame, "Giovanni", "1 Spade"))
  }

  test("captureCards should remove played card from player's hand") {
    val updatedGame = captureCards(sampleGame, "Giovanni", "7 Denari")
    assert(!updatedGame.playerHands("Giovanni").contains("7 Denari"))
  }

  test("captureCards should add played card to table if no match") {
    val updatedGame = MoveManager.captureCards(sampleGame, "Giovanni", "10 Spade")
    assert(updatedGame.tableCards.contains("10 Spade"))
  }

  test("handleDeckSteal should update player hands and captured decks") {
    val games = scala.collection.mutable.Map("game1" -> sampleGame)
    val deckStealResult = MoveManager.handleDeckSteal(sampleGame, "Giovanni", "7 Denari", games, players)
    assert(deckStealResult.nonEmpty)
    assert(!games("game1").playerHands("Giovanni").contains("7 Denari"))
  }

  test("handleMove should correctly update game state") {
    val games = scala.collection.mutable.Map("game1" -> sampleGame)
    MoveManager.handleMove(games, "game1", "Giovanni", "7 Denari")
    assert(!games("game1").playerHands("Giovanni").contains("7 Denari"))
  }


}

