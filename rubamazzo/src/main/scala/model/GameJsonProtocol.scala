package model

import spray.json._
import model.Game


/**
 * Defines JSON serialization and deserialization for the Game case class.
 * Uses Spray JSON to convert Game objects into JSON format and vice versa.
 *
 * @see Game The case class representing the state of a game.
 */
object GameJsonProtocol extends DefaultJsonProtocol {

  /**
   * Implicit format for the Game class.
   * Enables automatic conversion between Game objects and their JSON representation.
   */
  implicit val gameFormat: RootJsonFormat[Game] = jsonFormat7(Game) // Adjust to match the number of fields in the Game class
}



