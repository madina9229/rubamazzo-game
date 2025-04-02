# rubamazzo-game

Rubamazzo Distributed Game
Project Description
This project involves developing a distributed version of the card game Rubamazzo, implemented as a command-line application using a client-server architecture. The goal is to apply distributed systems concepts for efficient communication and data exchange between the client and server, using the Scala programming language.

Features

- Multiplayer: Supports multiple players in the same game.

- Game Rules: Follows the traditional rules of Rubamazzo.

- Client Interface: Command-line-based for players to interact with the server, make moves, and view game state.

- Central Server: Handles game logic, game state, and player turns.

- REST Communication: Utilizes HTTP with REST APIs for client-server interaction.

Handling Edge Cases
- Invalid Game ID: Returns 404 Not Found for non-existing game IDs.

- Duplicate Player Name: Prevents adding the same player twice, responding with 400 Bad Request.

- Player Not Part of Game: Rejects actions from players not in the game, with an appropriate error.

- Missing Parameters: Handles missing query parameters like playerName with a 400 Bad Request.

- No Players in the Game: Returns an error when querying the current turn in a game without players.

- Out-of-Turn Move: Rejects moves made by players when it's not their turn.
- Current Player Disconnects: If the current player (whose turn it is) disconnects:
The turn passes to the next player in line.
If there are no remaining players, the server responds with an appropriate message or handles the game termination.
- WebSocket Integration:
Provides real-time updates for player actions like joining, disconnecting, or reconnecting.
If a player disconnects, the server broadcasts a message to other connected players.
Ensures all active players are notified of important events during the game.