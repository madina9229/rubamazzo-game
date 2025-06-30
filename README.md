# Rubamazzo Multiplayer – Distributed Systems Project

This project implements a distributed, turn-based multiplayer card game inspired by the Italian classic *Rubamazzo*. The system adopts a client–server architecture in which multiple clients, running via Command-Line Interface (CLI), interact with a backend server developed in Scala using Akka HTTP and Akka Actors. Communication occurs through stateless RESTful APIs.

The game supports key features such as move validation, card capturing, deck stealing, turn rotation, and fault-tolerant handling of temporary disconnections and timeouts. Game state is held in-memory on the server and modeled immutably to ensure consistency, reliability, and thread-safe updates throughout gameplay.

For a comprehensive discussion of the system design, component diagrams, API logic, implementation constraints, and testing methodology, please refer to the full documentation. 

## Requirements

- **Java:** Version 8 or higher
- **Scala:** 2.13.12
- **sbt (Scala Build Tool)**
- **Git:** To clone the repository

## Installation and Running

1. **Clone the Repository**
   ```bash
   git clone https://github.com/madina9229/rubamazzo-game.git

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
