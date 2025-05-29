package scala

import org.scalatest.funsuite.AnyFunSuite
import scala.collection.immutable.Map
import server.{GameManager, PlayerManager, MoveManager, TimeoutManager}
import model.Game

class ReconnectPlayerTest extends AnyFunSuite {

  val players = List("Giovanni", "Marco")

  val sampleGame = Game(
    id = "testGame1",
    players = players,
    currentTurn = 0,
    playerHands = Map("Giovanni" -> List("Re Bastoni", "1 Spade", "Re Spade"), "Marco" -> List("7 Coppe", "3 Denari", "Fante Bastoni")),
    capturedDecks = Map("Giovanni" -> List("4 Bastoni", "4 Coppe")),
    deck = List("10 Spade", "2 Denari", "6 Bastoni"),
    disconnectedPlayers = List(),
    startingHandSize = 3
  )

  GameManager.games("testGame1") = sampleGame
  GameManager.originalPlayerOrders += ("testGame1" -> GameManager.games("testGame1").players.zipWithIndex.toMap)


  test("Player reconnects within timeout and keeps their original hand and captured deck") {
    PlayerManager.handleDisconnection(GameManager.games, "testGame1", "Giovanni")
    Thread.sleep(5000) // Simulates a fast reconnection
    val reconnectMessage = PlayerManager.reconnectPlayer(GameManager.games, "testGame1", "Giovanni")
    val updatedGame = GameManager.games("testGame1")
    println(s"Stato di Giovanni dopo disconnessione: ${GameManager.games("testGame1").playerHands.get("Giovanni")}")

    assert(updatedGame.playerHands("Giovanni") == List("Re Bastoni", "1 Spade", "Re Spade"), "Giovanni should recover original hand")
    assert(updatedGame.capturedDecks("Giovanni") == List("4 Bastoni", "4 Coppe"), "Giovanni should recover his captured deck")
    assert(updatedGame.disconnectedPlayers.isEmpty, "Giovanni should be removed from disconnected list")
    assert(reconnectMessage.contains("successfully reconnected to game"), "Reconnection message should confirm success")
    assert(TimeoutManager.getLastAction("Giovanni").isDefined, "Giovanni's action should be registered AFTER reconnection")


  }


  val disconnectionTimeout = 120000 // 2 minuti

  val sampleGame2 = Game(
    id = "testGame2",
    players = players,
    currentTurn = 0,
    playerHands = Map("Giovanni" -> List("Re Bastoni", "1 Spade", "Re Spade"), "Marco" -> List("7 Coppe", "3 Denari", "Fante Bastoni")),
    capturedDecks = Map("Giovanni" -> List("4 Bastoni", "4 Coppe")),
    deck = List("10 Spade", "2 Denari", "6 Bastoni"),
    disconnectedPlayers = List(),
    startingHandSize = 3
  )

  GameManager.games("testGame2") = sampleGame2
  GameManager.originalPlayerOrders += ("testGame2" -> GameManager.games("testGame2").players.zipWithIndex.toMap)

  test("Player reconnects after timeout and loses their hand and captured deck") {
    PlayerManager.handleDisconnection(GameManager.games, "testGame2", "Giovanni")
    Thread.sleep(disconnectionTimeout + 60000) // Simulates a late reconnection
    val reconnectMessage = PlayerManager.reconnectPlayer(GameManager.games, "testGame2", "Giovanni")
    val updatedGame = GameManager.games("testGame2")
    println(s" Giovanni esiste ancora dopo timeout? ${updatedGame.playerHands.contains("Giovanni")}")

    assert(!updatedGame.playerHands.contains("Giovanni"), "Giovanni should be completely removed after timeout expires")
    // Ensure timeout was enforced
    assert(TimeoutManager.getLastAction("Giovanni").isEmpty, "Giovanni's action should not be registered after timeout")

  }

}
