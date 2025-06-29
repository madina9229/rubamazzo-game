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
import scala.concurrent.{Future, blocking}
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import java.net.URLEncoder
import spray.json._
import spray.json.DefaultJsonProtocol._
import utils.Utils.displayCard


object Client {

  implicit val system: ActorSystem = ActorSystem("Client")
  implicit val materializer: Materializer = Materializer(system)
  val serverUrl = "http://localhost:8080/game"
  var activePlayers = scala.collection.mutable.Set[String]()


  def validateNonEmptyInput(input: String, fieldName: String): String = {
    if (input.trim.isEmpty) {
      println(s"Error: $fieldName cannot be empty. Please enter a valid value.")
      StdIn.readLine().trim
    } else input
  }

  def createGame(): Future[String] = {
    Http().singleRequest(Post(s"$serverUrl/createGame")).flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          response.entity.toStrict(3.seconds).map(_.data.utf8String).map { gameId =>
            gameId.split("ID: ")(1).trim
          }
        case _ =>
          Future.failed(new Exception(s"Failed to create game, server response: ${response.status}"))
      }
    }
  }

  def startGame(gameId: String, playerName: String): Future[String] = {
    Http().singleRequest(Post(s"$serverUrl/start/$gameId")).flatMap { response =>
      response.entity.toStrict(3.seconds).map(_.data.utf8String).map { result =>
        println(s"Game Started: $result\n")
        Await.result(getGameState(gameId, playerName), 10.seconds)
        result
      }
    }
  }


  def joinGame(gameId: String, playerName: String): Future[String] = {
    Http().singleRequest(Get(s"$serverUrl/join/$gameId/$playerName")).flatMap { response =>
      response.entity.toStrict(3.seconds).map(_.data.utf8String).map { result =>
        //Await.result(getGameState(gameId, playerName), 10.seconds)
        result
      }
    }
  }

  def makeMove(gameId: String, playerName: String, move: String): Future[String] = {
    val moveEncoded = URLEncoder.encode(move, "UTF-8")
    println(s"Sending request: $serverUrl/makeMove/$gameId?playerName=$playerName&move=$moveEncoded")
    val request = Post(s"$serverUrl/makeMove/$gameId?playerName=$playerName&move=$moveEncoded")
    Http().singleRequest(request).flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          response.entity.toStrict(3.seconds).map(_.data.utf8String).map { result =>
            println(s"Move Made: $result\n")
            result
          }
        case _ =>
          Future.failed(new Exception(s"Failed to make move, server response: ${response.status}"))
      }
    }
  }

  def getGameState(gameId: String, playerName: String): Future[String] = {
    Http().singleRequest(Get(s"$serverUrl/gameState/$gameId/$playerName")).flatMap { response =>
      response.entity.toStrict(3.seconds).map(_.data.utf8String).map { state =>
        //println(state)
        state
      }
    }
  }

  def disconnectPlayer(gameId: String, playerName: String): Future[String] = {
    activePlayers.remove(playerName)
    println("Disconnecting")
    println("Your hand may be affected if you are inactive for too long")
    Http().singleRequest(Post(s"$serverUrl/disconnectPlayer/$gameId?playerName=$playerName")).flatMap { response =>
      response.entity.toStrict(3.seconds).map(_.data.utf8String).map { result =>
        println(s"\n**Disconnection** $playerName has disconnected!\nUpdated game state:")
        //Await.result(getGameState(gameId), 10.seconds)
        result
      }
    }
  }

  def reconnectPlayer(gameId: String, playerName: String): Future[String] = {
    println("If you rejoin within the allowed time, your hand will be restored")
    println("If you exceeded the timeout, your cards may be redistributed")
    Http().singleRequest(Post(s"$serverUrl/reconnectPlayer/$gameId?playerName=$playerName")).flatMap { response =>
      response.entity.toStrict(3.seconds).map(_.data.utf8String).map { result =>
        result
      }
    }
  }


  var keepUpdating = true

  def continuousGameStateUpdate(gameId: String, playerName: String): Unit = {
    var previousState = ""
    while (keepUpdating) {
      Thread.sleep(1000)
      val currentState = Await.result(getGameState(gameId, playerName), 10.seconds)
      if (currentState != previousState) {
        println("\n Game status updated:")
        println(currentState)
        previousState = currentState
      }


    }
  }

  def main(args: Array[String]): Unit = {
    println("Welcome to the game! Do you want to create a new game or join an existing one? (Create/Join)")

    var action = ""
    while (!Set("create", "join", "exit").contains(action)) {
      action = StdIn.readLine().toLowerCase.trim
    }

    if (action == "exit") {
      println("Exiting selection...")
      return
    }

    println(s"You selected: $action")

    val gameId = if (action == "create") {
      val createdGameId = Await.result(createGame(), 10.seconds)
      createdGameId
    } else {
      println("Enter the ID of the game you want to join:")
      validateNonEmptyInput(StdIn.readLine().trim, "Game ID")
    }
    println("Enter your name:")
    val playerName = validateNonEmptyInput(StdIn.readLine().trim, "Player Name")
    val creator = if (action == "create") playerName else ""
    var gameStateThread:Thread = null


    joinGame(gameId, playerName).onComplete {
      case Success(response) =>
        println(s"\nResult: $response")
        keepUpdating = true
        gameStateThread = new Thread(() => continuousGameStateUpdate(gameId, playerName))
        gameStateThread.setDaemon(true)
        gameStateThread.start()

      case Failure(ex) => println(s"Error joining game: ${ex.getMessage}")
    }

    gameLoop(gameId, playerName, creator)
  }



  def gameLoop(gameId: String, playerName: String, creator: String): Unit = {
      var isConnected = true
      var previousState = ""


      while (isConnected) {
        Thread.sleep(1000)
        val state = Await.result(getGameState(gameId, playerName), 10.seconds)


        println("\nWhat do you want to do?")
        println("[1] Start the game")
        println("[2] Make a move")
        println("[3] View game state")
        println("[4] Disconnect")
        println("[5] Reconnect")
        println("[6] Exit")

        val input = StdIn.readLine().trim

        if (input.isEmpty) {
          println("No command entered. Please type a valid option.")
        } else {
          input match {
            case "1" if playerName == creator =>
              val result = Await.result(startGame(gameId, playerName), 10.seconds)
              println("\nGame started!")
            case "1" =>
              println("\nOnly the game creator can start the game!")

            case "2" =>
                println("Enter your move (e.g., '10 of Coppe'):")
                val move = StdIn.readLine().trim
                if (move.nonEmpty) {
                  makeMove(gameId, playerName, move).onComplete {
                    case Success(response) =>
                      println(s"\n$response\n")
                    case Failure(ex) => println(s"\nError making move: ${ex.getMessage}\n")
                  }
                } else {
                  println("Invalid move. Please enter a valid move.")
                }

            case "3" =>
              println("\nChecking the game state...\n")
              val state = Await.result(getGameState(gameId, playerName), 10.seconds)
              println(state)

            case "4" =>
              println("Disconnecting player...")
              val response = Await.result(disconnectPlayer(gameId, playerName), 10.seconds)
              println(s"\n**Disconnection:** $response\n")
              println("You may reconnect with command [5] within the time limit.")

            case "5" =>
              println("Reconnecting player...")
              val response = Await.result(reconnectPlayer(gameId, playerName), 10.seconds)
              println(s"\n**Reconnection:** $response\n")
              if ((response.contains("has ended because only one player remained")) || (response.contains("exceeded timeout and was removed"))) {
                println(s"Player $playerName exceeded the timeout and was removed from the game. Closing game...")
                keepUpdating = false
                isConnected = false
                system.terminate()
              } else {
                println("Reconnection successful!")
              }

            case "6" =>
              println("Exiting the game...")
              isConnected = false
              system.terminate()
              return

            case _ =>
              println("Invalid option, please try again!")
          }
        }
      }


  }

}

