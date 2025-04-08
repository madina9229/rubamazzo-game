package model

/**
 * Represents the state of a single game.
 * This class tracks players, disconnected players, the turn order, cards on the table,
 * player hands, and captured decks.
 *
 * @param id The unique identifier for the game.
 * @param players The list of players currently in the game.
 * @param disconnectedPlayers The list of players who have disconnected from the game.
 * @param currentTurn The index of the current player in the `players` list.
 * @param tableCards The list of cards currently visible on the table.
 * @param playerHands A mapping of each player's name to the list of cards in their hand.
 * @param capturedDecks A mapping of each player's name to the list of cards they have captured.
 *                      Default value is an empty list for all players.
 */
case class Game(
                 id: String,
                 players: List[String],
                 disconnectedPlayers: List[String] = List(),
                 currentTurn: Int = 0,
                 tableCards: List[String] = List(),
                 playerHands: Map[String, List[String]] = Map(),
                 capturedDecks: Map[String, List[String]] = Map().withDefaultValue(List())

               )



