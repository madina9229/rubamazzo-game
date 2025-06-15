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
 * @param deck The list of remaining cards that have not been distributed yet.
 * @param startingHandSize The number of cards each player receives at the beginning of each distribution round.
 *                         This value determines how many cards are dealt when players run out of cards in hand.
 *                         Default value is 3, following traditional game rules.
 * @param turnCompleted A mapping of each player's name to whether they have completed their turn.
 *                      Default value is `false` for all players, indicating that they have yet to finish their turn.
 */
case class Game(
                 id: String,
                 players: List[String],
                 disconnectedPlayers: List[String] = List(),
                 currentTurn: Int = 0,
                 tableCards: List[String] = List(),
                 playerHands: Map[String, List[String]] = Map(),
                 capturedDecks: Map[String, List[String]] = Map(),
                 deck: List[String] = List(),
                 startingHandSize: Int = 3,
                 turnCompleted: Map[String, Boolean] = Map().withDefaultValue(false),
                 gameOver: Boolean = false,
                 winner: Option[String] = None
               )



