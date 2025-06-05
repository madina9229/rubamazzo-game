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
import java.net.URLEncoder
import spray.json._
import spray.json.DefaultJsonProtocol._
import utils.Utils.displayCard


object Client {

  implicit val system: ActorSystem = ActorSystem("Client")
  implicit val materializer: Materializer = Materializer(system)
  val serverUrl = "http://localhost:8080/game"
  var activePlayers = scala.collection.mutable.Set[String]()


  def createGame(): Future[String] = {
    Http().singleRequest(Post(s"$serverUrl/createGame")).flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          response.entity.toStrict(3.seconds).map(_.data.utf8String).map { gameId =>
            println(s"Game Created: $gameId\n")
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


        if (state.contains("Game over!")) {
          println("\n The game has ended! Here are the final results: ")
          val winner = state.split("\n").find(_.contains("Winner")).getOrElse("No winner found")
          println(s"$winner ")
          println(state)
        }
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
    println("Reconnecting")
    println("If you rejoin within the allowed time, your hand will be restored")
    println("If you exceeded the timeout, your cards may be redistributed")
    getGameState(gameId, playerName).flatMap { state =>
      if (!state.contains(playerName)) {
        println("Server removed player, reconnection not possible.")
        Future.successful("Reconnection failed: Player removed from game.")
      } else {
        println("Reconnection authorized, proceeding...")
        Http().singleRequest(Post(s"$serverUrl/reconnectPlayer/$gameId?playerName=$playerName")).flatMap { response =>
          response.entity.toStrict(3.seconds).map(_.data.utf8String).map { result =>
            println(s" $playerName is back in the game! Status updated.")
            result
          }
        }
      }
    }
  }

  def checkTimeout(playerName: String): Future[String] = {
    Http().singleRequest(Get(s"$serverUrl/checkTimeout/$playerName")).flatMap { response =>
      response.entity.toStrict(3.seconds).map(_.data.utf8String)
    }
  }

  def getActivePlayers(gameId: String): Future[Set[String]] = {
    Http().singleRequest(Get(s"$serverUrl/activePlayers/$gameId")).flatMap { response =>
      response.entity.toStrict(3.seconds).map(_.data.utf8String).map { playersJson =>
        playersJson.parseJson.convertTo[Set[String]] // Assicurati che il server restituisca un Set di nomi
      }
    }
  }



  //___________________________________________________________



  def main(args: Array[String]): Unit = {
    println("Welcome to the game! Do you want to create a new game or join an existing one? (Create/Join)")

    var action = ""

    while (!Set("create", "join", "exit").contains(action)) {
      action = StdIn.readLine().toLowerCase.trim

      /*if (action.isEmpty || !Set("create", "join", "exit").contains(action)) {
        println("Invalid choice. Please enter 'Create', 'Join', or 'Exit'.")
      }*/
    }


    if (action == "exit") {
      println("Exiting selection...")
      return
    }

    println(s"You selected: $action")

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

    gameLoop(gameId, playerName)
  }


  def gameLoop(gameId: String, playerName: String): Unit = {
      var isConnected = true
      var previousState = ""
      val initialState = Await.result(getGameState(gameId, playerName), 10.seconds)
      //val state = Await.result(getGameState(gameId, playerName), 10.seconds)
      if (initialState.contains("Game over!")) {
        println("\n The game has ended! Congratulations to the winner!")
        println(initialState)
        system.terminate()
        return
      }
      previousState = initialState
      while (isConnected) {
        Thread.sleep(1000)
        val state = Await.result(getGameState(gameId, playerName), 10.seconds)

        if (state.contains("Game over!")) {
          println("\n The game has ended! Congratulations to the winner!")
          println(state)
          isConnected = false
          system.terminate()
          return
        }


        if (state != previousState) {
          println("\n Updated game state:")
          println(state)
          previousState = state
        }




        println("\nWhat do you want to do?")
        println("[1] Start the game")
        println("[2] Make a move")
        println("[3] View game state")
        println("[4] Disconnect")
        println("[5] Reconnect")
        println("[6] Check timeout status")
        println("[7] Exit")

        val input = StdIn.readLine().trim

        if (input.isEmpty) {
          println("No command entered. Please type a valid option.")
        } else {
          input match {
            case "1" =>
              val result = Await.result(startGame(gameId, playerName), 10.seconds)

            case "2" =>
              println("Enter your move (e.g., '10 of Coppe'):")
              val move = StdIn.readLine().trim
              if (move.nonEmpty) {
                makeMove(gameId, playerName, move).onComplete {
                  case Success(response) => println(s"\n$response\n")
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
              //isConnected = false

            case "5" =>
              println("Reconnecting player...")
              val response = Await.result(reconnectPlayer(gameId, playerName), 10.seconds)
              println(s"\n**Reconnection:** $response\n")
              //isConnected = true

            case "6" =>
              val timeoutStatus = Await.result(checkTimeout(playerName), 10.seconds)
              println(s"\n**Timeout Status:**\n$timeoutStatus\n")

            case "7" =>
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

