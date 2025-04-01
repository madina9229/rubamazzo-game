package server

import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl._
import scala.collection.concurrent.TrieMap
import akka.stream.BoundedSourceQueue
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.QueueOfferResult




object WebSocketHandler {

  // Mappa per mantenere le connessioni attive(playerName -> queue)
  private val connections = TrieMap[String, BoundedSourceQueue[TextMessage]]()




  def addConnection(playerName: String, queue: BoundedSourceQueue[TextMessage]): Unit = {
    connections.put(playerName, queue)
  }


  def removeConnection(playerName: String): Unit = {
    connections.remove(playerName)
  }

  def broadcastToOtherClients(message: TextMessage): Unit = {
    connections.values.foreach { queue =>
      queue.offer(message) match {
        case QueueOfferResult.Enqueued =>
          println("Message enqueued successfully")
        case QueueOfferResult.Dropped =>
          println("Message dropped (queue is full)")
        case QueueOfferResult.Failure(ex) =>
          println(s"Failed to enqueue message: ${ex.getMessage}")
        case QueueOfferResult.QueueClosed =>
          println("Queue was closed, unable to send message")
      }
    }
  }

  // Flusso WebSocket per gestire i messaggi e connessioni
  def webSocketFlow(playerName: String): Flow[Message, Message, Any] = {
    Flow.fromSinkAndSourceCoupled(
      Sink.ignore, // Gestione dei messaggi in entrata
      Source.queue[TextMessage](50).mapMaterializedValue { queue =>
        addConnection(playerName, queue)
        // Gestione della disconnessione
        Flow[Message].watchTermination() { (_, termination) =>
          termination.onComplete { _ =>
            WebSocketHandler.removeConnection(playerName)
            val notification = TextMessage(s"Player $playerName has disconnected.")
            WebSocketHandler.broadcastToOtherClients(notification)
          }(scala.concurrent.ExecutionContext.global)
        }
        queue
      }
    )
  }



}






