package client

import scala.io.StdIn
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl._
import akka.stream.Materializer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Success, Failure}


object Client {

  implicit val system: ActorSystem = ActorSystem("Client")
  implicit val materializer: Materializer = Materializer(system)

  val serverUrl = "http://localhost:8080/game"

  def createGame(): Future[String] = {
    Http().singleRequest(Post(s"$serverUrl/createGame")).flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          response.entity.toStrict(3.seconds).map(_.data.utf8String).map { gameId =>
            println(s"\n**Game Created**: ID → $gameId\n")
            gameId.split("ID: ")(1).trim
          }
        case _ =>
          Future.failed(new Exception(s"Failed to create game, server response: ${response.status}"))
      }
    }
  }

  def startGame(gameId: String): Future[String] = {
    Http().singleRequest(Post(s"$serverUrl/start/$gameId")).flatMap { response =>
      response.entity.toStrict(3.seconds).map(_.data.utf8String).map { result =>
        println(s"\n**Game Started**: $result\n")
        Await.result(getGameState(gameId), 10.seconds)
        result
      }
    }
  }


  def joinGame(gameId: String, playerName: String): Future[String] = {
    Http().singleRequest(Get(s"$serverUrl/join/$gameId/$playerName")).flatMap { response =>
      response.entity.toStrict(3.seconds).map(_.data.utf8String).map { result =>
        println(s"\n**Player Joined**: $playerName\nUpdated game state:")
        Await.result(getGameState(gameId), 10.seconds)
        result
      }
    }
  }

  def handleMove(gameId: String, playerName: String, playedCard: String): Unit = {
    val gameState = Await.result(getGameState(gameId), 10.seconds)
    val playerHand = extractPlayerHand(gameState, playerName)
    val formattedMove = playedCard.trim
      .replaceAll("\\s+of\\s+", " of ")
      .replaceAll("\\s+", " of ")
      .split("\\s+").map(_.capitalize).mkString(" ")
    if (!playerHand.contains(formattedMove)) {
      println(s"Error: $playerName does not have the card '$formattedMove'.")
      return
    }
    val result = Await.result(makeMove(gameId, playerName, formattedMove), 10.seconds)
    println(s"\nMove Update: $playerName played '$formattedMove'.\n$result\n")
    Await.result(getGameState(gameId), 10.seconds)
  }

  def extractPlayerHand(gameState: String, playerName: String): List[String] = {
    val handStart = gameState.indexOf(s""""$playerName": [""") + playerName.length + 5
    val handEnd = gameState.indexOf("]", handStart)
    if (handStart > 4 && handEnd > handStart) {
      gameState.substring(handStart, handEnd)
        .split(",")
        .map(_.trim.replace("\"", "").replaceAll("\\s+", " of ").split("\\s+").map(_.capitalize).mkString(" "))
        .toList
    } else {
      List.empty
    }
  }

  def makeMove(gameId: String, playerName: String, move: String): Future[String] = {
    val encodedMove = java.net.URLEncoder.encode(move, "UTF-8")
    Http().singleRequest(Post(s"$serverUrl/makeMove/$gameId?playerName=$playerName&move=$encodedMove")).flatMap { response =>
      response.entity.toStrict(3.seconds).map(_.data.utf8String).map { result =>
        if (response.status == StatusCodes.OK) {
          result
        } else {
          s"Error: Move could not be processed. Server responded with: ${response.status}"
        }
      }
    }.recover { case ex =>
      s"Failed to make move. Reason: ${ex.getMessage}"
    }
  }


  def getGameState(gameId: String): Future[String] = {
    Http().singleRequest(Get(s"$serverUrl/gameState/$gameId")).flatMap { response =>
      response.entity.toStrict(3.seconds).map(_.data.utf8String).map { state =>
        println(s"\n**Game Status:**\n$state\n")
        state
      }
    }
  }


  def disconnectPlayer(gameId: String, playerName: String): Future[String] = {
    println("Disconnecting")
    println("Your hand may be affected if you are inactive for too long")
    println("You can reconnect anytime using option four")
    Http().singleRequest(Post(s"$serverUrl/disconnectPlayer/$gameId?playerName=$playerName")).flatMap { response =>
      response.entity.toStrict(3.seconds).map(_.data.utf8String).map { result =>
        println(s"\n**Disconnection** $playerName has disconnected!\nUpdated game state:")
        Await.result(getGameState(gameId), 10.seconds)
        result
      }
    }
  }

  def reconnectPlayer(gameId: String, playerName: String): Future[String] = {
    println("Reconnecting")
    println("If you rejoin within the allowed time, your hand will be restored")
    println("If you exceeded the timeout, your cards may be redistributed")
    Http().singleRequest(Post(s"$serverUrl/reconnectPlayer/$gameId?playerName=$playerName")).flatMap { response =>
      response.entity.toStrict(3.seconds).map(_.data.utf8String).map { result =>
        println(s"\n**Reconnection** $playerName is back!\nUpdated game state:")
        Await.result(getGameState(gameId), 10.seconds)
        result
      }
    }
  }

  def checkTimeout(playerName: String): Future[String] = {
    Http().singleRequest(Get(s"$serverUrl/checkTimeout/$playerName")).flatMap { response =>
      response.entity.toStrict(3.seconds).map(_.data.utf8String)
    }
  }

  def updateTurn(gameId: String): Future[String] = {
    Http().singleRequest(Post(s"$serverUrl/updateTurn/$gameId")).flatMap { response =>
      response.entity.toStrict(3.seconds).map(_.data.utf8String).map { result =>
        println(s"\n**Turn Update** $result\n")
        Await.result(getGameState(gameId), 10.seconds)
        result
      }
    }
  }


  def connectWebSocket(playerName: String): Unit = {
    val seenMessages = scala.collection.mutable.Set[String]()
    val flow = Flow[Message].collect {
      case TextMessage.Strict(text) =>
        val normalizedText = text.replaceAll(
          """[

        \[\]

        :]""", ""
        ).trim.toLowerCase
        if (!seenMessages.contains(normalizedText)) {
          seenMessages.add(normalizedText)
          println(s"\n [Server Update]: $normalizedText\n")
        }
        TextMessage(normalizedText)
    }
    val webSocketFuture = Http().singleWebSocketRequest(WebSocketRequest(s"ws://localhost:8080/game/connectPlayer/$playerName"), flow)._1
    webSocketFuture.failed.foreach { ex =>
      println(s"WebSocket connection failed: ${ex.getMessage}")
    }

  }



  def main(args: Array[String]): Unit = {
    println("Welcome to the game! Do you want to create a new game or join an existing one? (Create/Join)")
    var action = ""

    while (action != "create" && action != "join") {
      action = StdIn.readLine().toLowerCase().trim
      if (action != "create" && action != "join") {
        println("Invalid choice. Please enter 'Create' or 'Join'.")
      }
    }

    val gameId = if (action == "create") {
      val createdGameId = Await.result(createGame(), 10.seconds)
      println(s"\nGame created with ID: $createdGameId")
      createdGameId
    } else {
      println("Enter the ID of the game you want to join:")
      StdIn.readLine().trim
    }

    println("Enter your name:")
    val playerName = StdIn.readLine().trim

    joinGame(gameId, playerName).onComplete {
      case Success(response) => println(s"\nResult: $response")
      case Failure(ex) => println(s"Error joining game: ${ex.getMessage}")
    }

    try {
      connectWebSocket(playerName)
    } catch {
      case ex: Exception => println(s"Error connecting to WebSocket: ${ex.getMessage}")
    }

    gameLoop(gameId, playerName)
  }

  def gameLoop(gameId: String, playerName: String): Unit = {
    var isConnected = true
    while (isConnected) {
      Thread.sleep(500)
      println("\nWhat do you want to do?")
      println("[1] Make a move")
      println("[2] View game state")
      println("[3] Disconnect")
      println("[4] Reconnect")
      println("[5] Check timeout status")
      println("[6] Start the game")
      println("[7] Update turn")
      println("[8] Exit")

      val choice = StdIn.readLine()

      choice match {
        case "1" =>
          println("Enter your move:")
          val move = StdIn.readLine().trim
          handleMove(gameId, playerName, move)

        case "2" =>
          println("Checking the game state")
          println("This will show")
          println("- Active players and disconnected ones")
          println("- Cards currently on the table")
          println("- Remaining deck cards")
          println("- Whose turn it is")
          Await.result(getGameState(gameId), 10.seconds)

        case "3" =>
          Await.result(disconnectPlayer(gameId, playerName), 10.seconds)
          isConnected = false

        case "4" =>
          Await.result(reconnectPlayer(gameId, playerName), 10.seconds)
          isConnected = true

        case "5" =>
          Await.result(checkTimeout(playerName), 10.seconds)

        case "6" =>
          Await.result(startGame(gameId), 10.seconds)

        case "7" =>
          Await.result(updateTurn(gameId), 10.seconds)

        case "8" =>
          println("Exiting the game...")
          system.terminate()
          return

        case _ =>
          println("Invalid option, please try again!")
      }
    }
  }

}

