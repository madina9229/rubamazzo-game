package scala
import org.scalatest.funsuite.AnyFunSuite
import scala.collection.immutable.Map
import server.MoveManager
import server.PlayerManager
import model.Game

class EdgeCasesTest extends AnyFunSuite {

  val players = List("Giovanni", "Marco")

  test("Player plays last card and captures all table cards") {
    val game = Game(
      id = "game1",
      players = players,
      currentTurn = 0,
      playerHands = Map("Giovanni" -> List("8 Denari"), "Marco" -> List("7 Spade")),
      tableCards = List("5 Denari", "3 Spade"),
      capturedDecks = Map("Giovanni" -> List(), "Marco" -> List()),
      deck = List(),
      disconnectedPlayers = List(),
      startingHandSize = 2,
      turnCompleted = Map().withDefaultValue(false)
    )

    val games = scala.collection.mutable.Map("game1" -> game)
    val updatedGame = MoveManager.captureCards(game, "Giovanni", "8 Denari")

    assert(updatedGame.tableCards.isEmpty, "Table should be empty after full capture")
    assert(updatedGame.capturedDecks("Giovanni").contains("8 Denari"), "Captured deck should contain played card")
  }

  test("Player plays a card that does not match any on the table") {
    val game = Game(
      id = "game2",
      players = players,
      currentTurn = 0,
      playerHands = Map("Giovanni" -> List("1 Coppe"), "Marco" -> List("7 Spade")),
      tableCards = List("5 Denari", "3 Spade"),
      capturedDecks = Map("Giovanni" -> List(), "Marco" -> List()),
      deck = List(),
      disconnectedPlayers = List(),
      startingHandSize = 2,
      turnCompleted = Map().withDefaultValue(false)
    )

    val games = scala.collection.mutable.Map("game2" -> game)
    val updatedGame = MoveManager.captureCards(game, "Giovanni", "1 Coppe")

    assert(updatedGame.tableCards.contains("1 Coppe"), "Played card should be added to the table")
  }

  test("Player tries to play a card they don't have") {
    val game = Game(
      id = "game3",
      players = players,
      currentTurn = 0,
      playerHands = Map("Giovanni" -> List("2 Bastoni"), "Marco" -> List("7 Spade")),
      tableCards = List("5 Denari", "3 Spade"),
      capturedDecks = Map("Giovanni" -> List(), "Marco" -> List()),
      deck = List(),
      disconnectedPlayers = List(),
      startingHandSize = 2,
      turnCompleted = Map().withDefaultValue(false)
    )

    assert(!MoveManager.validateCard(game, "Giovanni", "10 Spade"), "Player should not be allowed to play a card they don't have")
  }

  test("Disconnected player should not be able to play a move") {
    val game = Game(
      id = "game4",
      players = players,
      currentTurn = 0,
      playerHands = Map("Giovanni" -> List("3 Denari"), "Marco" -> List("7 Spade")),
      tableCards = List("5 Denari", "4 Coppe"),
      capturedDecks = Map("Giovanni" -> List(), "Marco" -> List()),
      deck = List(),
      disconnectedPlayers = List("Giovanni"),
      startingHandSize = 2,
      turnCompleted = Map().withDefaultValue(false)
    )

    val activePlayers = players.filterNot(game.disconnectedPlayers.contains)
    val isValidTurn = MoveManager.validatePlayerAndTurn(game, "Giovanni", activePlayers)

    assert(!isValidTurn, "Disconnected player should not be allowed to play a move")
  }

  test("Player successfully steals the deck with their last card and updates capturedDeck") {
    val game = Game(
      id = "game5",
      players = players,
      currentTurn = 0,
      playerHands = Map("Giovanni" -> List("Re Bastoni"), "Marco" -> List("7 Spade")),
      tableCards = List(),
      capturedDecks = Map("Giovanni" -> List("Re Bastoni", "Re Coppe", "5 Spade", "5 Bastoni", "Re Spade", "4 Bastoni", "4 Coppe"), "Marco" -> List("Re Coppe", "5 Spade", "5 Bastoni", "Re Spade")),
      deck = List(),
      disconnectedPlayers = List(),
      startingHandSize = 2,
      turnCompleted = Map().withDefaultValue(false)
    )

    val games = scala.collection.mutable.Map("game5" -> game)
    val stealResult = MoveManager.handleDeckSteal(game, "Giovanni", "Re Bastoni", games, players)

    assert(stealResult.nonEmpty, "Player should successfully steal the deck")
    assert(!games("game5").playerHands("Giovanni").contains("Re Bastoni"), "Player's hand should be empty after playing last card")
  }

  test("Player disconnects but remaining player hands remain unchanged") {
    val gameBeforeDisconnect = Game(
      id = "testGame",
      players = players,
      currentTurn = 0,
      playerHands = Map("Catia" -> List("Re Bastoni", "1 Spade", "Re Spade"), "Mirko" -> List("1 Bastoni", "3 Denari", "Fante Bastoni"), "Sara" -> List("7 Denari", "9 Spade", "3 Bastoni")),
      disconnectedPlayers = List(),
      startingHandSize = 3,
      turnCompleted = Map().withDefaultValue(false)
    )

    val games = scala.collection.mutable.Map("testGame" -> gameBeforeDisconnect)

    PlayerManager.handleDisconnection(games, "testGame", "Catia")

    val updatedGame = games("testGame")

    assert(updatedGame.playerHands("Mirko") == gameBeforeDisconnect.playerHands("Mirko"), "Mirko's hand should remain unchanged")
    assert(updatedGame.playerHands("Sara") == gameBeforeDisconnect.playerHands("Sara"), "Sara's hand should remain unchanged")
  }


}




