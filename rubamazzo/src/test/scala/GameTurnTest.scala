package scala

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import server.{GameManager, PlayerManager, MoveManager, TimeoutManager}
import model.Game

class GameTurnTest extends AnyFunSuite with Matchers {


  test("MoveManager allows a player to steal an opponent's deck and advance the turn") {

    val players = List("Catia", "Mirko", "Sara")
    val initialHands = Map(
      "Catia" -> List("8 of Spade", "5 of Coppe", "4 of Bastoni"),
      "Mirko" -> List("Fante of Coppe", "3 of Coppe", "1 of Bastoni"),
      "Sara" -> List("Cavallo of Bastoni", "2 of Coppe", "2 of Bastoni")
    )
    val capturedDecks = Map(
      "Catia" -> List("7 of Spade", "7 of Bastoni"),
      "Mirko" -> List(),
      "Sara" -> List("8 of Coppe", "8 of Bastoni")
    )

    val game = Game(
      id = "game1",
      players = players,
      playerHands = initialHands,
      tableCards = List("5 Denari", "3 Spade"),
      capturedDecks = capturedDecks,
      currentTurn = 0, // Catia's turn
      turnCompleted = Map().withDefaultValue(false)
    )
    GameManager.games("game1") = game

    // Catia plays "8 of Spade" attempting to steal Sara's deck
    MoveManager.handleMove(GameManager.games, "game1", "Catia", "8 of Spade")
    val updatedGame = GameManager.games("game1")
    //println(s"Steal result: $stealResult")

    // Validate deck stealing
    updatedGame.capturedDecks("Catia") should contain allElementsOf List("8 of Spade", "8 of Coppe", "8 of Bastoni", "7 of Spade", "7 of Bastoni")
    updatedGame.capturedDecks("Sara") shouldBe empty // Sara's deck should be emptied

    //  Ensure turn has advanced
    updatedGame.currentTurn shouldBe 1 // Expect Mirko's turn after Catia

  }



}
