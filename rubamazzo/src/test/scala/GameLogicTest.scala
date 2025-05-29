package scala

import model.Game
import server.GameManager
import server.PlayerManager
import server.MoveManager
import server.MoveManager._
import org.scalatest.funsuite.AnyFunSuite
import scala.collection.immutable.Map

class GameLogicTest extends AnyFunSuite {

  val gameId = "gameMove"
  val players = List("Giovanni", "Marco")

  val sampleGame = Game(
    id = gameId,
    players = players,
    currentTurn = 0,
    playerHands = Map("Giovanni" -> List("7 Denari", "Re Bastoni"), "Marco" -> List("5 Coppe", "1 Spade")),
    tableCards = List("6 Bastoni", "Fante Coppe"),
    capturedDecks = Map(
      "Giovanni" -> List(),
      "Marco" -> List("7 Spade", "7 Bastoni")),
    deck = List("10 Spade", "Fante Denari", "2 Bastoni"),
    disconnectedPlayers = List(),
    startingHandSize = 2,
    turnCompleted = Map().withDefaultValue(false)
  )

  GameManager.games("gameMove") = sampleGame

  test("validateGameExists should return Some(game) if game exists") {
    assert(MoveManager.validateGameExists(GameManager.games, gameId).nonEmpty)
  }

  test("validateGameExists should return None if game does not exist") {
    assert(MoveManager.validateGameExists(GameManager.games, "non-existing-game").isEmpty)
  }

  test("validatePlayerAndTurn should return true if it's the player's turn") {
    assert(MoveManager.validatePlayerAndTurn(GameManager.games(gameId), "Giovanni", players))
  }

  test("validatePlayerAndTurn should return false if not player's turn") {
    assert(!MoveManager.validatePlayerAndTurn(GameManager.games(gameId), "Marco", players))
  }

  test("validateCard should return true if player has the card") {
    assert(MoveManager.validateCard(GameManager.games(gameId), "Giovanni", "7 Denari"))
  }

  test("validateCard should return false if player does not have the card") {
    assert(!MoveManager.validateCard(GameManager.games(gameId), "Giovanni", "1 Spade"))
  }

  test("captureCards should remove played card from player's hand") {
    val updatedGame = MoveManager.captureCards(GameManager.games(gameId), "Giovanni", "7 Denari")
    GameManager.games.update(gameId, updatedGame)
    assert(!GameManager.games(gameId).playerHands("Giovanni").contains("7 Denari"))
  }

  test("captureCards should add played card to table if no match") {
    val updatedGame = MoveManager.captureCards(GameManager.games(gameId), "Giovanni", "10 Spade")
    GameManager.games.update(gameId, updatedGame)
    assert(GameManager.games(gameId).tableCards.contains("10 Spade"))
  }

  test("handleDeckSteal should update player hands, captured decks, and advance the turn") {
    val stealDeckResult = MoveManager.handleDeckSteal(GameManager.games(gameId), "Giovanni", "7 Denari", GameManager.games, players)
    assert(stealDeckResult.contains("Giovanni stole the deck from Marco"))
    val updatedGame = GameManager.games(gameId)
    assert(!updatedGame.playerHands("Giovanni").contains("7 Denari"))
    assert(updatedGame.capturedDecks("Giovanni").contains("7 Denari"))
    assert(updatedGame.capturedDecks("Giovanni").contains("7 Spade"))
    assert(updatedGame.capturedDecks("Giovanni").contains("7 Bastoni"))
    assert(updatedGame.capturedDecks("Marco").isEmpty)
    assert(updatedGame.currentTurn != 0, "Turn should not remain on Giovanni after stealing!")
  }

  test("handleMove should correctly update game state") {
    MoveManager.handleMove(GameManager.games, gameId, "Giovanni", "7 Denari")
    assert(!GameManager.games(gameId).playerHands("Giovanni").contains("7 Denari"))
  }
}
