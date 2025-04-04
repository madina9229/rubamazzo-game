package server

import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl._
import scala.collection.concurrent.TrieMap
import akka.stream.BoundedSourceQueue
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.QueueOfferResult
import akka.actor.ActorSystem
import akka.event.Logging




object WebSocketHandler {

  private val system = ActorSystem("WebSocketHandler")
  private val log = Logging(system, getClass)
  // Mappa per mantenere le connessioni attive(playerName -> queue)
  private val connections = TrieMap[String, BoundedSourceQueue[TextMessage]]()





  def addConnection(playerName: String, queue: BoundedSourceQueue[TextMessage]): Unit = {
    connections.put(playerName, queue)
    log.info(s"Player $playerName connected")
  }

  def removeConnection(playerName: String): Unit = {
    connections.remove(playerName)
    log.info(s"Player $playerName disconnected")
  }

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

  // Flusso WebSocket per gestire i messaggi e connessioni
  def webSocketFlow(playerName: String): Flow[Message, Message, Any] = {
    log.info(s"Setting up WebSocket flow for player: $playerName")
    Flow.fromSinkAndSourceCoupled(
      Sink.ignore, // Gestione dei messaggi in entrata
      Source.queue[TextMessage](50).mapMaterializedValue { queue =>
        addConnection(playerName, queue)
        log.info(s"Player $playerName successfully connected via WebSocket")
        // Gestione della disconnessione
        Flow[Message].watchTermination() { (_, termination) =>
          termination.onComplete { _ =>
            log.info(s"Player $playerName has disconnected from WebSocket")
            WebSocketHandler.removeConnection(playerName)
            val notification = TextMessage(s"Player $playerName has disconnected.")
            log.info(s"Broadcasting disconnect message for $playerName")
            WebSocketHandler.broadcastToOtherClients(notification)
          }(scala.concurrent.ExecutionContext.global)
        }
        queue
      }
    )
  }



}






