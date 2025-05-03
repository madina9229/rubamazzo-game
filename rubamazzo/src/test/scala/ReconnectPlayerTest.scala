import org.scalatest.funsuite.AnyFunSuite
import scala.collection.immutable.Map
import server.MoveManager
import server.PlayerManager
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

  val games = scala.collection.mutable.Map("testGame1" -> sampleGame)

  test("Player reconnects within timeout and keeps their original hand and captured deck") {
    PlayerManager.handleDisconnection(games, "testGame1", "Giovanni")
    Thread.sleep(5000) // Simulates a fast reconnection
    val reconnectMessage = PlayerManager.reconnectPlayer(games, "testGame1", "Giovanni")
    val updatedGame = games("testGame1")

    assert(updatedGame.playerHands("Giovanni") == List("Re Bastoni", "1 Spade", "Re Spade"), "Giovanni should recover original hand")
    assert(updatedGame.capturedDecks("Giovanni") == List("4 Bastoni", "4 Coppe"), "Giovanni should recover his captured deck")
    assert(updatedGame.disconnectedPlayers.isEmpty, "Giovanni should be removed from disconnected list")
    assert(reconnectMessage.contains("successfully reconnected to game"), "Reconnection message should confirm success")
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

  val games2 = scala.collection.mutable.Map("testGame2" -> sampleGame2)

  test("Player reconnects after timeout and loses their hand and captured deck") {
    PlayerManager.handleDisconnection(games2, "testGame2", "Giovanni")
    Thread.sleep(disconnectionTimeout + 60000) // Simulates a late reconnection
    val reconnectMessage = PlayerManager.reconnectPlayer(games2, "testGame2", "Giovanni")
    val updatedGame = games2("testGame2")

    assert(updatedGame.playerHands("Giovanni").isEmpty, "Giovanni should lose his hand after long disconnection")
    assert(!updatedGame.capturedDecks.contains("Giovanni"), "Giovanni should lose his captured deck after long disconnection")
    assert(updatedGame.deck.contains("Re Bastoni"), "Giovanni's cards should be put back in deck")
    assert(!updatedGame.disconnectedPlayers.isEmpty, "Giovanni should not be removed from disconnected list")
    assert(reconnectMessage.contains("can not reconnect to the game"), "Reconnection message should confirm failure")
  }

}
