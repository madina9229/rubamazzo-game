package server

import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl._
import scala.collection.concurrent.TrieMap
import akka.stream.BoundedSourceQueue
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.QueueOfferResult
import akka.actor.ActorSystem
import akka.event.Logging
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import model.Game
import server.PlayerManager
import server.GameManager
import scala.concurrent.Future

object WebSocketHandler {

  private val system = ActorSystem("WebSocketHandler")
  private val log = Logging(system, getClass)
  // Map to maintain active connections (playerName -> queue)
  val connections = TrieMap[String, BoundedSourceQueue[TextMessage]]()
  val sentMessages = scala.collection.mutable.ListBuffer[String]()

  /**
   * Adds a new connection for a player.
   *
   * @param playerName Name of the player.
   * @param queue      The source queue used for WebSocket messaging.
   */
  def addConnection(playerName: String, queue: BoundedSourceQueue[TextMessage]): Unit = {
    connections.put(playerName, queue)
    log.info(s"Player $playerName connected via WebSocket.")
    // Send a periodic ping message to keep the connection alive
    system.scheduler.scheduleWithFixedDelay(10.seconds, 30.seconds) {
      new Runnable {
        def run(): Unit = {
          connections.get(playerName).foreach { queue =>
            queue.offer(TextMessage(s"ping $playerName"))
          }
        }
      }
    }(system.dispatcher)

    Future {
      Thread.sleep(500)
      broadcastToOtherClients(playerName, TextMessage(s"Player $playerName has connected."))
    }(ExecutionContext.global)
  }

  /**
   * Removes a player's connection.
   *
   * @param playerName Name of the player.
   */
  def removeConnection(games: scala.collection.mutable.Map[String, Game], playerName: String): Unit = {
    connections.remove(playerName)
    log.info(s"Player $playerName disconnected from WebSocket.")
    if (connections.contains(playerName)) {
      broadcastToOtherClients(playerName, TextMessage(s"Player $playerName has disconnected."))
      connections.remove(playerName)
    } else {
      log.warning(s"Skipping broadcast: $playerName is already removed from connections.")
    }
    // Handle player disconnection in the server
    games.foreach { case (gameId, game) =>
      if (game.players.contains(playerName)) {
        PlayerManager.handleDisconnection(GameManager.games, gameId, playerName)
      }
    }

  }


  def reconnectPlayer(playerName: String): Unit = {
    connections.get(playerName) match {
      case Some(queue) =>
        log.info(s"Player $playerName reconnected successfully.")
        queue.offer(TextMessage(s"Player $playerName is back in the game!"))

      case None =>
        log.warning(s"Player $playerName trying to reconnect but was removed.")
    }
  }


  /**
   * Broadcasts a message to all connected clients except the sender.
   *
   * @param message The message to broadcast.
   */
  def broadcastToOtherClients(sender: String, message: TextMessage): Unit = {
    message match {
      case TextMessage.Strict(text) =>
        val cleanedText = text.replaceAll(
          """[

          \[\]

          :]""", ""
        ).trim.toLowerCase
        if (!sentMessages.exists(msg => cleanedText.startsWith(msg))) {
          sentMessages += cleanedText

          connections.filterKeys(_ != sender).foreach { case (player, queue) =>
            queue.offer(TextMessage(s"Player $sender: connected")) match {
              case QueueOfferResult.Enqueued => log.info(s"Message broadcasted to $player successfully")
              case QueueOfferResult.Dropped => log.warning(s"Message dropped for $player (queue full)")
              case QueueOfferResult.Failure(ex) => log.error(s"Failed to broadcast to $player: ${ex.getMessage}")
              case QueueOfferResult.QueueClosed =>
                log.warning(s"Cannot broadcast to $player, queue is closed.")
                connections.remove(player)
            }
          }
        } else {
          log.info(s"Duplicate message ignored: $cleanedText")
        }
    }
  }

  def sendToClient(playerName: String, message: TextMessage): Unit = {
    connections.get(playerName) match {
      case Some(queue) =>
        queue.offer(message) match {
          case QueueOfferResult.Enqueued => log.info(s"Message sent to $playerName successfully")
          case QueueOfferResult.Dropped => log.warning(s"Message dropped for $playerName (queue full)")
          case QueueOfferResult.Failure(ex) => log.error(s"Failed to send message to $playerName: ${ex.getMessage}")
          case QueueOfferResult.QueueClosed => log.warning(s"Cannot send message to $playerName, queue is closed.")
        }
      case None =>
        log.warning(s"No WebSocket connection found for player $playerName.")
    }
  }


  /**
   * Handles WebSocket flow for a player.
   *
   * @param playerName Name of the player.
   * @return A Flow[Message, Message, Any] for WebSocket interactions.
   */
  def webSocketFlow(games: scala.collection.mutable.Map[String, Game], playerName: String)(implicit ec: ExecutionContext): Flow[Message, Message, Any] = {
    log.info(s"Setting up WebSocket flow for player: $playerName.")

    // Sink to process incoming messages
    val messageSink = Sink.foreach[Message] {
      case TextMessage.Strict(text) =>
        log.info(s"Received message from $playerName: $text.")
        broadcastToOtherClients(playerName, TextMessage(s"[$playerName]: $text"))
      case _ =>
        log.warning(s"Unsupported message type received from $playerName.")
    }
    // Source to send outgoing messages
    val messageSource = Source
      .queue[TextMessage](50)
      .mapMaterializedValue { queue =>
        addConnection(playerName, queue)
        log.info(s"WebSocket flow established for player: $playerName.")
        Flow[Message].watchTermination() { (_, termination) =>
          termination.onComplete { _ =>
            log.info(s"Player $playerName WebSocket connection terminated.")
            reconnectPlayer(playerName)
            games.foreach { case (gameId, game) =>
              if (game.players.contains(playerName)) {
                PlayerManager.handleDisconnection(GameManager.games, gameId, playerName)
              }
            }
            removeConnection(games, playerName)
          }

        }
        queue
      }

    Flow.fromSinkAndSourceCoupled(messageSink, messageSource)
  }




}






