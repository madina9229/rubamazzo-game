import scala.io.StdIn
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import akka.util.ByteString
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._

object Client extends App {
  implicit val system: ActorSystem = ActorSystem("RubamazzoClient")
  implicit val materializer: Materializer = Materializer(system)
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  val serverUrl = "http://localhost:8080"

  def joinGame(gameId: String, playerName: String): Future[HttpResponse] = {
    val request = Post(s"$serverUrl/joinGame/$gameId?playerName=$playerName")
    Http().singleRequest(request)
  }

  def makeMove(gameId: String, playerName: String, move: String): Future[HttpResponse] = {
    val request = Post(s"$serverUrl/makeMove/$gameId?playerName=$playerName&move=$move")
    Http().singleRequest(request)
  }

  def getGameState(gameId: String): Future[HttpResponse] = {
    val request = Get(s"$serverUrl/gameState/$gameId")
    Http().singleRequest(request)
  }

  def disconnectPlayer(gameId: String, playerName: String): Future[HttpResponse] = {
    val request = Post(s"$serverUrl/disconnectPlayer/$gameId?playerName=$playerName")
    Http().singleRequest(request)
  }

  def reconnectPlayer(gameId: String, playerName: String): Future[HttpResponse] = {
    val request = Post(s"$serverUrl/reconnectPlayer/$gameId?playerName=$playerName")
    Http().singleRequest(request)
  }

  println("Welcome to the Rubamazzo client!")
  var running = true
  var showCommands = true

  while (running) {
    if (showCommands) {
      println("\nAvailable commands:")
      println("- join: Join a game (requires game ID and player name)")
      println("- move: Make a move (requires game ID, player name, and move)")
      println("- state: View the game state (requires game ID)")
      println("- disconnect: Disconnect from the game (requires game ID and player name)")
      println("- reconnect: Reconnect to the game (requires game ID and player name)")
      println("- exit: Exit the client")
      showCommands = false
    }
    println("\nEnter a command:")
    val input = StdIn.readLine().trim // Rimuove eventuali spazi vuoti

    if (input.isEmpty) {
      println("No command entered. Please type a command from the list.")
    } else {
      input.split(" ").toList match {
        case "join" :: gameId :: playerName :: Nil =>
          Await.result(joinGame(gameId, playerName).map(response => println(response.entity)), 5.seconds)
        case "move" :: gameId :: playerName :: move :: Nil =>
          Await.result(makeMove(gameId, playerName, move).map(response => println(response.entity)), 5.seconds)
        case "state" :: gameId :: Nil =>
          Await.result(getGameState(gameId).map(response => println(response.entity)), 5.seconds)
        case "disconnect" :: gameId :: playerName :: Nil =>
          Await.result(disconnectPlayer(gameId, playerName).map(response => println(response.entity)), 5.seconds)
        case "reconnect" :: gameId :: playerName :: Nil =>
          Await.result(reconnectPlayer(gameId, playerName).map(response => println(response.entity)), 5.seconds)
        case "exit" :: Nil =>
          running = false
        case _ =>
          println("Invalid command. Please try again.")
      }
    }
  }


  system.terminate()
}
