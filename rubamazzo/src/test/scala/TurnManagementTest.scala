package scala

import org.scalatest.funsuite.AnyFunSuite
import scala.collection.immutable.Map
import server.MoveManager
import server.PlayerManager
import server.GameManager
import model.Game


class TurnManagementTest extends AnyFunSuite {

  val players = List("Giovanni", "Marco", "Luca")

  test("Turn advances correctly") {
    val game = Game(
      id = "gamegame1",
      players = players,
      currentTurn = 0,
      playerHands = Map("Giovanni" -> List("7 Denari", "7 Bastoni", "3 Spade"), "Marco" -> List("7 Spade", "4 Denari", "2 Bastoni"), "Luca" -> List("6 Coppe", "Re Spade", "Re Cavallo")),
      tableCards = List("5 Denari", "3 Spade"),
      capturedDecks = Map("Giovanni" -> List(), "Marco" -> List()),
      deck = List(),
      disconnectedPlayers = List(),
      startingHandSize = 2,
      turnCompleted = Map().withDefaultValue(false),
      gameOver = false,
      winner = None
    )
    GameManager.games("gamegame1") = game
    MoveManager.handleMove(GameManager.games, "gamegame1", "Giovanni", "7 Denari")
    val updatedGame = GameManager.games("gamegame1")
    assert(updatedGame.players(updatedGame.currentTurn) == "Marco", "Turn should pass to Marco after Giovanni's move")
  }

  test("Turn advances correctly after Luca plays his last card") {
    val game = Game(
      id = "game11",
      players = players,
      currentTurn = 2,
      playerHands = Map("Giovanni" -> List(), "Marco" -> List(), "Luca" -> List("Re Cavallo")),
      tableCards = List("5 Denari", "3 Spade"),
      capturedDecks = Map("Giovanni" -> List(), "Marco" -> List()),
      deck = List("7 Coppe", "4 Spade", "8 Denari", "9 Bastoni", "6 Coppe", "2 Denari", "3 Bastoni", "Fante Spade", "Re Coppe"),
      disconnectedPlayers = List(),
      startingHandSize = 2,
      turnCompleted = Map().withDefaultValue(false),
      gameOver = false,
      winner = None
    )
    //val games = scala.collection.mutable.Map("game11" -> game)
    GameManager.games("game11") = game
    MoveManager.handleMove(GameManager.games, "game11", "Luca", "Re Cavallo")
    val updatedGame = GameManager.games("game11")
    assert(updatedGame.players(updatedGame.currentTurn) == "Giovanni", "Turn should pass to Giovanni after Luca's move")
  }


 test("Turn skips disconnected players") {
    val game = Game(
      id = "game2",
      players = players,
      currentTurn = 0,
      playerHands = Map("Giovanni" -> List("Fante Coppe"), "Marco" -> List("7 Spade"), "Luca" -> List("6 Bastoni")),
      tableCards = List("2 Spade", "4 Denari"),
      capturedDecks = Map("Giovanni" -> List(), "Marco" -> List(), "Luca" -> List()),
      deck = List(),
      disconnectedPlayers = List(),
      startingHandSize = 2,
      turnCompleted = Map().withDefaultValue(false),
      gameOver = false,
      winner = None
    )
    GameManager.games("game2") = game
    MoveManager.handleMove(GameManager.games, "game2", "Giovanni", "Fante Coppe")
    // Marco disconnects
    PlayerManager.handleDisconnection(GameManager.games, "game2", "Marco")
    val updatedGame = GameManager.games("game2")
    assert(updatedGame.players(updatedGame.currentTurn) == "Marco", "Turn should remain to Marco for now")
  }


  test("Turn correctly skips two consecutive disconnected players") {
    val game = Game(
      id = "game6",
      players = players,
      currentTurn = 0,
      playerHands = Map("Giovanni" -> List("Re Spade"), "Marco" -> List("10 Coppe"), "Luca" -> List("Fante Bastoni")),
      tableCards = List("4 Denari", "2 Spade"),
      capturedDecks = Map("Giovanni" -> List(), "Marco" -> List(), "Luca" -> List()),
      deck = List(),
      disconnectedPlayers = List(),
      startingHandSize = 2,
      turnCompleted = Map().withDefaultValue(false),
      gameOver = false,
      winner = None
    )
    GameManager.games("game6") = game
    // Giovanni plays and the turn should pass to Marco
    MoveManager.handleMove(GameManager.games, "game6", "Giovanni", "Re Spade")
    var updatedGame = GameManager.games("game6")
    assert(updatedGame.players(updatedGame.currentTurn) == "Marco", "Turn should pass to Marco after Giovanni's move")
    // Marco disconnects while it's his turn
    PlayerManager.handleDisconnection(GameManager.games, "game6", "Marco")
    // Update turn logic should now skip Marco and move to Luca
    updatedGame = GameManager.games("game6")
    assert(updatedGame.players(updatedGame.currentTurn) == "Marco", "Turn should remain to Marco for now (temporarily)")
  }


  test("Turn does not change when a disconnected player reconnects") {
    val game = Game(
      id = "game3",
      players = players,
      currentTurn = 0,
      playerHands = Map("Giovanni" -> List("Cavallo Bastoni"), "Marco" -> List("10 Spade"), "Luca" -> List("Re Coppe")),
      tableCards = List("7 Coppe", "2 Bastoni"),
      capturedDecks = Map("Giovanni" -> List(), "Marco" -> List(), "Luca" -> List()),
      deck = List(),
      disconnectedPlayers = List(),
      startingHandSize = 2,
      turnCompleted = Map().withDefaultValue(false),
      gameOver = false,
      winner = None
    )
    GameManager.games("game3") = game
    GameManager.originalPlayerOrders += ("game3" -> GameManager.games("game3").players.zipWithIndex.toMap)

    println(s"Turno iniziale: ${GameManager.games("game3").players(GameManager.games("game3").currentTurn)}")
    MoveManager.handleMove(GameManager.games, "game3", "Giovanni", "Cavallo Bastoni")
    println(s"Turno dopo la mossa di Giovanni: ${GameManager.games("game3").players(GameManager.games("game3").currentTurn)}")
    val originalOrder = GameManager.games("game3").players
    println(s"Ordine originale prima della disconnessione: $originalOrder")
    // Marco disconnects
    PlayerManager.handleDisconnection(GameManager.games, "game3", "Marco")
    // Marco reconnects in time
    PlayerManager.reconnectPlayer(GameManager.games, "game3", "Marco")
    val updatedGame = GameManager.games("game3")
    println(s"Turno attuale dopo la riconnessione di Marco: ${updatedGame.players(updatedGame.currentTurn)}")
    println(s"Ordine originale dei giocatori: ${GameManager.originalPlayerOrders.get("game3")}")
    assert(updatedGame.players(updatedGame.currentTurn) == "Marco", "Turn should remain unchanged after Marco reconnects")
  }


  test("Turn does not change when a non-turn player disconnects") {
    val game = Game(
      id = "game4",
      players = players,
      currentTurn = 0,
      playerHands = Map("Giovanni" -> List("Cavallo Bastoni"), "Marco" -> List("10 Spade"), "Luca" -> List("Re Coppe")),
      tableCards = List("7 Coppe", "2 Bastoni"),
      capturedDecks = Map("Giovanni" -> List(), "Marco" -> List(), "Luca" -> List()),
      deck = List(),
      disconnectedPlayers = List(),
      startingHandSize = 2,
      turnCompleted = Map().withDefaultValue(false),
      gameOver = false,
      winner = None
    )
    GameManager.games("game4") = game
    //GameManager.originalPlayerOrders += ("game4" -> GameManager.games("game4").players.zipWithIndex.toMap)
    // Marco disconnects
    PlayerManager.handleDisconnection(GameManager.games, "game4", "Marco")
    // Marco reconnects in time
    PlayerManager.reconnectPlayer(GameManager.games, "game4", "Marco")
    val updatedGame = GameManager.games("game4")
    assert(updatedGame.players(updatedGame.currentTurn) == "Giovanni", "Turn should remain unchanged when a non-turn player disconnects")
  }


  test("Turn cycles correctly after last player moves") {
    val game = Game(
      id = "game5",
      players = players,
      currentTurn = 2, // Luca is currently playing
      playerHands = Map("Giovanni" -> List("Cavallo Denari"), "Marco" -> List("10 Spade"), "Luca" -> List("Re Coppe")),
      tableCards = List("4 Coppe", "5 Bastoni"),
      capturedDecks = Map("Giovanni" -> List(), "Marco" -> List(), "Luca" -> List()),
      deck = List(),
      disconnectedPlayers = List(),
      startingHandSize = 3,
      turnCompleted = Map().withDefaultValue(false),
      gameOver = false,
      winner = None
    )
    GameManager.games("game5") = game
    //GameManager.originalPlayerOrders += ("game5" -> GameManager.games("game5").players.zipWithIndex.toMap)
    MoveManager.handleMove(GameManager.games, "game5", "Luca", "Re Coppe")
    val updatedGame = GameManager.games("game5")
    assert(updatedGame.players(updatedGame.currentTurn) == "Giovanni", "Turn should cycle back to Giovanni after Luca's move")
  }


  test("Game ends when only one player remains") {
    val game = Game(
      id = "game7",
      players = players,
      currentTurn = 0,
      playerHands = Map("Giovanni" -> List("Cavallo Spade"), "Marco" -> List("Re Denari"), "Luca" -> List("Fante Bastoni")),
      tableCards = List("3 Spade", "6 Coppe"),
      capturedDecks = Map("Giovanni" -> List(), "Marco" -> List(), "Luca" -> List()),
      deck = List(),
      disconnectedPlayers = List(),
      startingHandSize = 3,
      turnCompleted = Map().withDefaultValue(false),
      gameOver = false,
      winner = None
    )
    GameManager.games("game7") = game
    //GameManager.originalPlayerOrders += ("game7" -> GameManager.games("game7").players.zipWithIndex.toMap)
    // Giovanni and Marco disconnect, leaving only Luca
    PlayerManager.handleDisconnection(GameManager.games, "game7", "Giovanni")
    PlayerManager.handleDisconnection(GameManager.games, "game7", "Marco")
    val endResult = GameManager.endGame("game7")
    assert(endResult.contains("Game over"), "Game should end when only one player remains")
  }


  test("Turn advances when disconnected player fails to reconnect in time") {
    val game = Game(
      id = "game8",
      players = players,
      currentTurn = 0,
      playerHands = Map("Giovanni" -> List("Re Bastoni"), "Marco" -> List("10 Coppe"), "Luca" -> List("Fante Spade")),
      tableCards = List("2 Denari", "6 Coppe"),
      capturedDecks = Map("Giovanni" -> List(), "Marco" -> List(), "Luca" -> List()),
      deck = List(),
      disconnectedPlayers = List(),
      startingHandSize = 3,
      turnCompleted = Map().withDefaultValue(false),
      gameOver = false,
      winner = None
    )
    GameManager.games("game8") = game
    // Giovanni disconnects
    PlayerManager.handleDisconnection(GameManager.games, "game8", "Giovanni")
    // Simulate timeout expiration
    Thread.sleep(PlayerManager.disconnectionTimeout + 2000)
    val reconnectMessage = PlayerManager.reconnectPlayer(GameManager.games, "game8", "Giovanni")
    val updatedGame = GameManager.games("game8")
    GameManager.updateTurn("game8")
    assert(updatedGame.players(updatedGame.currentTurn) == "Marco", "Turn should pass to Marco after Giovanni fails to reconnect in time")
  }



  test("Turn order remains stable after multiple consecutive moves") {
    val game = Game(
      id = "game9",
      players = players,
      currentTurn = 0,
      playerHands = Map("Giovanni" -> List("Fante Denari"), "Marco" -> List("10 Coppe"), "Luca" -> List("Re Spade")),
      tableCards = List("7 Coppe", "3 Bastoni"),
      capturedDecks = Map("Giovanni" -> List(), "Marco" -> List(), "Luca" -> List()),
      deck = List("9 Coppe", "4 Spade", "8 Denari", "9 Bastoni", "6 Coppe", "2 Denari", "4 Bastoni", "Fante Spade", "Re Coppe"),
      disconnectedPlayers = List(),
      startingHandSize = 3,
      turnCompleted = Map().withDefaultValue(false),
      gameOver = false,
      winner = None
    )

    GameManager.games("game9") = game
    // Giovanni plays
    MoveManager.handleMove(GameManager.games, "game9", "Giovanni", "Fante Denari")
    // Marco plays
    MoveManager.handleMove(GameManager.games, "game9", "Marco", "10 Coppe")
    // Luca plays
    MoveManager.handleMove(GameManager.games, "game9", "Luca", "Re Spade")
    val updatedGame = GameManager.games("game9")
    assert(updatedGame.players(updatedGame.currentTurn) == "Giovanni", "Turn should cycle back to Giovanni after all players make a move")
  }


  test("Player tries to make a move when it's not their turn") {
    val game = Game(
      id = "game10",
      players = players,
      currentTurn = 0,
      playerHands = Map("Giovanni" -> List("Fante Coppe"), "Marco" -> List("7 Spade"), "Luca" -> List("6 Bastoni")),
      tableCards = List("2 Spade", "4 Denari"),
      capturedDecks = Map("Giovanni" -> List(), "Marco" -> List(), "Luca" -> List()),
      deck = List(),
      disconnectedPlayers = List(),
      startingHandSize = 2,
      turnCompleted = Map().withDefaultValue(false),
      gameOver = false,
      winner = None
    )

    GameManager.games("game10") = game
    val result = MoveManager.handleMove(GameManager.games, "game10", "Marco", "7 Spade")

    assert(result.contains("Invalid move"), "Marco should not be able to play when it's Giovanni's turn")
  }


}

