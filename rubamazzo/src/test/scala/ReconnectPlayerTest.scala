package scala

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.collection.immutable.Map
import server.{GameManager, PlayerManager, MoveManager}
import model.Game

class ReconnectPlayerTest extends AnyFunSuite with Matchers {

  val players = List("Giovanni", "Marco")

  val reconnectGame = Game(
    id = "reconnectGameId",
    players = players,
    currentTurn = 0,
    playerHands = Map("Giovanni" -> List("Re Bastoni", "1 Spade", "Re Spade"), "Marco" -> List("7 Coppe", "3 Denari", "Fante Bastoni")),
    capturedDecks = Map("Giovanni" -> List("4 Bastoni", "4 Coppe")),
    deck = List("10 Spade", "2 Denari", "6 Bastoni"),
    disconnectedPlayers = List(),
    startingHandSize = 3,
    turnCompleted = Map().withDefaultValue(false),
    gameOver = false,
    winner = None
  )

  GameManager.games("reconnectGameId") = reconnectGame
  GameManager.originalPlayerOrders += ("reconnectGameId" -> GameManager.games("reconnectGameId").players.zipWithIndex.toMap)

  test("Player reconnects within timeout and keeps their original hand and captured deck") {
    PlayerManager.handleDisconnection(GameManager.games, "reconnectGameId", "Giovanni")
    Thread.sleep(5000) // Simulates a fast reconnection
    val reconnectMessage = PlayerManager.reconnectPlayer(GameManager.games, "reconnectGameId", "Giovanni")
    val updatedGame = GameManager.games("reconnectGameId")
    println(s"Stato di Giovanni dopo disconnessione: ${GameManager.games("reconnectGameId").playerHands.get("Giovanni")}")

    updatedGame.playerHands("Giovanni").contains("Re Bastoni")
    updatedGame.capturedDecks("Giovanni").contains("4 Bastoni")
    updatedGame.disconnectedPlayers shouldBe empty
    reconnectMessage should include("successfully reconnected to game")

  }


  val disconnectionTimeout = 120000 // 2 minuti

  val reconnectGame1 = Game(
    id = "reconnectGameId1",
    players = players,
    currentTurn = 0,
    playerHands = Map("Giovanni" -> List("Re Bastoni", "1 Spade", "Re Spade"), "Marco" -> List("7 Coppe", "3 Denari", "Fante Bastoni")),
    capturedDecks = Map("Giovanni" -> List("4 Bastoni", "4 Coppe")),
    deck = List("10 Spade", "2 Denari", "6 Bastoni"),
    disconnectedPlayers = List(),
    startingHandSize = 3,
    turnCompleted = Map().withDefaultValue(false),
    gameOver = false,
    winner = None
  )

  GameManager.games("reconnectGameId1") = reconnectGame1
  //GameManager.originalPlayerOrders += ("reconnectGameId1" -> GameManager.games("reconnectGameId1").players.zipWithIndex.toMap)

  test("Player reconnects after timeout and loses their hand and captured deck") {
    PlayerManager.handleDisconnection(GameManager.games, "reconnectGameId1", "Giovanni")
    Thread.sleep(disconnectionTimeout + 60000) // Simulates a late reconnection
    val reconnectMessage = PlayerManager.reconnectPlayer(GameManager.games, "reconnectGameId1", "Giovanni")
    val updatedGame = GameManager.games("reconnectGameId1")

    updatedGame.playerHands.contains("Giovanni") shouldBe false

  }

}



