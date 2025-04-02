package model

import spray.json._
import model.Game

object GameJsonProtocol extends DefaultJsonProtocol {
  implicit val gameFormat: RootJsonFormat[Game] = jsonFormat4(Game)
}


