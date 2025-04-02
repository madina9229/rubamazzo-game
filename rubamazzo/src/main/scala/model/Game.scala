package model


case class Game(
                 id: String,
                 players: List[String],
                 disconnectedPlayers: List[String] = List(),
                 currentTurn: Int = 0
               )


