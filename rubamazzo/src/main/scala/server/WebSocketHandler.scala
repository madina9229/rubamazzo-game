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


object WebSocketHandler {

  private val system = ActorSystem("WebSocketHandler")
  private val log = Logging(system, getClass)
  // Map to maintain active connections (playerName -> queue)
  private val connections = TrieMap[String, BoundedSourceQueue[TextMessage]]()


  /**
   * Adds a new connection for a player.
   *
   * @param playerName Name of the player.
   * @param queue      The source queue used for WebSocket messaging.
   */
  def addConnection(playerName: String, queue: BoundedSourceQueue[TextMessage]): Unit = {
    connections.put(playerName, queue)
    log.info(s"Player $playerName connected via WebSocket.")
    broadcastToOtherClients(TextMessage(s"Player $playerName has connected."))
  }

  /**
   * Removes a player's connection.
   *
   * @param playerName Name of the player.
   */
  def removeConnection(games: scala.collection.mutable.Map[String, Game], playerName: String): Unit = {
    connections.remove(playerName)
    log.info(s"Player $playerName disconnected from WebSocket.")
    broadcastToOtherClients(TextMessage(s"Player $playerName has disconnected."))
    // Handle player disconnection in the server
    games.foreach { case (gameId, game) =>
      if (game.players.contains(playerName)) {
        PlayerManager.handleDisconnection(GameManager.games, gameId, playerName)
      }
    }

  }


  /**
   * Broadcasts a message to all connected clients except the sender.
   *
   * @param message The message to broadcast.
   */
  def broadcastToOtherClients(message: TextMessage): Unit = {
    connections.values.foreach { queue =>
      queue.offer(message) match {
        case QueueOfferResult.Enqueued =>
          println("Message enqueued successfully")
          log.info("Message broadcasted successfully")
        case QueueOfferResult.Dropped =>
          println("Message dropped (queue is full)")
          log.warning("Message dropped (queue is full)")
        case QueueOfferResult.Failure(ex) =>
          log.error(s"Failed to broadcast message: ${ex.getMessage}")
          println(s"Failed to enqueue message: ${ex.getMessage}")
        case QueueOfferResult.QueueClosed =>
          println("Queue was closed, unable to send message")
          log.error("Queue closed, unable to broadcast message")
      }
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
        broadcastToOtherClients(TextMessage(s"[$playerName]: $text"))
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
            games.foreach { case (gameId, game) =>
              if (game.players.contains(playerName)) {
                PlayerManager.handleDisconnection(GameManager.games, gameId, playerName) // Handle disconnection
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






