package server

import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl._

object WebSocketHandler {
  def webSocketFlow(playerName: String): Flow[Message, Message, Any] = {
    Flow[Message].map {
      case TextMessage.Strict(text) =>
        TextMessage(s"Hello $playerName! Waiting for updates...")
      case _ =>
        TextMessage("Unsupported message type")
    }
  }
}






