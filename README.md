# Rubamazzo Multiplayer Card Game

This project implements a distributed multiplayer card game inspired by the classic Rubamazzo. The game is playable via a Command-Line Interface (CLI) and uses a REST API built with **Scala** and **Akka HTTP** to manage communications between clients and the server.

## Overview

The project aims to create a card game inspired by Rubamazzo, where players can create or join games, make moves, capture cards, and even handle temporary disconnections and timeouts. The system relies on a centralized model in which the server maintains the game state in memory and interacts with clients through HTTP requests.

## Features

- **Game Creation:**
    - The system automatically generates a unique Game ID for each new game using a GUID/UUID generator.
    - This ensures that every game session is uniquely identifiable, eliminating the possibility of conflicts.

- **Joining a Game:**
    - New players can join an existing game by providing a valid Game ID.
    - The system verifies that the Game ID exists and that the player is not already registered in that session.
    - It also manages the player list and updates turn order accordingly to ensure smooth gameplay.

- **Move Handling:**
    - Players perform moves based on the rules of Rubamazzo, which include capturing cards on the table, forming valid combinations, or even stealing an opponent's deck under specific conditions.
    - Each move is validated against the current game state to prevent illegal actions.
    - The server processes the move, updates the game state immediately, and manages turn rotation.

- **Game State Update:**
    - After every action, the system updates the complete game state and delivers it to the clients in real time.
    - The state includes detailed information such as the cards on the table, cards in hand, current player's turn, captured cards, and any other relevant game data.
    - This information is formatted in JSON for easy readability and integration with various client applications.

- **Disconnection/Timeout Management:**
    - The system monitors player connectivity and employs a timeout mechanism to handle temporary disconnections.
    - If a player disconnects, the server retains their state for a specified timeout period, allowing for reconnections without immediate game disruption.
    - Should the timeout be exceeded, the system automatically removes the player from the game to maintain fairness and flow.

- **Distributed System:**
    - The project uses a client-server structure where the server maintains the entire game state in memory and handles all game logic.
    - Communication between clients and the server is carried out over a RESTful API, which simplifies network interactions and state synchronization.
    - This modular design ensures the system is scalable and can be extended or integrated with additional features, such as a graphical interface or persistent storage, in future updates.

## Architecture

The system adopts a **distributed client-server architecture**:
- **Client:** Interacts via a CLI and sends HTTP requests to the server to perform actions (join, move, request state, etc.).
- **Server:** Implemented using **Akka HTTP** on **Scala**, it handles game logic, deals cards, and maintains the game state in memory (using a `Map[String, Game]`).

## Requirements

- **Java:** Version 8 or higher
- **Scala:** 2.13.12
- **sbt (Scala Build Tool)**
- **Git:** To clone the repository

## Installation and Running

1. **Clone the Repository**
   ```bash
   git clone 

   cd rubamazzo-game

2.  **Build and Run**
    - to start the server, execute:
      ```bash
      sbt run
    - choose option "2" server.Server
    
    The server will start and listen on http://localhost:8080/game/.

    - open a new terminal in the same directory and, to start the client, execute:
      ```bash
      sbt run.
    - choose option "1" client.Client
    
    Note (Windows users): Before running the application in your terminal, you may want to enable UTF-8 support to display special characters correctly:
      ```bash
      chcp 65001
    
    
   This ensures proper rendering of accented letters, card symbols, and any non-ASCII characters.

   Follow the CLI prompts to join a game or create a new session.
