#!/bin/bash

BASE_URL="http://localhost:8080/game"

echo "### Creazione del gioco ###"
GAME_ID=$(curl -X POST "$BASE_URL/createGame" | awk '{print $NF}')
echo "Game ID: $GAME_ID"
echo -e "\n-----------------------------\n"

echo "### Aggiunta giocatori al gioco ###"
curl -X GET "$BASE_URL/join/$GAME_ID/Catia"
curl -X GET "$BASE_URL/join/$GAME_ID/Mirko"
curl -X GET "$BASE_URL/join/$GAME_ID/Sara"
echo -e "\n-----------------------------\n"

echo "### Avvio della partita ###"
curl -X GET "$BASE_URL/start/$GAME_ID"
echo -e "\n-----------------------------\n"

echo "### Stato iniziale della partita ###"
curl -X GET "$BASE_URL/gameState/$GAME_ID"
echo -e "\n-----------------------------\n"

echo "### Disconnessione di Sara (non di turno) ###"
curl -X POST "$BASE_URL/disconnectPlayer/$GAME_ID?playerName=Sara"
curl -X GET "$BASE_URL/gameState/$GAME_ID"
echo -e "\n-----------------------------\n"

echo "### Riconnessione di Sara ###"
curl -X POST "$BASE_URL/reconnectPlayer/$GAME_ID?playerName=Sara"
curl -X GET "$BASE_URL/gameState/$GAME_ID"
echo -e "\n-----------------------------\n"

echo "### Disconnessione del giocatore di turno (Catia) ###"
curl -X POST "$BASE_URL/disconnectPlayer/$GAME_ID?playerName=Catia"
curl -X GET "$BASE_URL/gameState/$GAME_ID"
echo -e "\n-----------------------------\n"

echo "### Riconnessione di Catia ###"
curl -X POST "$BASE_URL/reconnectPlayer/$GAME_ID?playerName=Catia"
curl -X GET "$BASE_URL/gameState/$GAME_ID"
echo -e "\n-----------------------------\n"


echo "### Disconnessione simultanea di Mirko e Sara ###"
curl -X POST "$BASE_URL/disconnectPlayer/$GAME_ID?playerName=Mirko"
curl -X POST "$BASE_URL/disconnectPlayer/$GAME_ID?playerName=Sara"
curl -X GET "$BASE_URL/gameState/$GAME_ID"
echo -e "\n-----------------------------\n"

echo "### Riconnessione di Mirko e Sara ###"
curl -X POST "$BASE_URL/reconnectPlayer/$GAME_ID?playerName=Mirko"
curl -X POST "$BASE_URL/reconnectPlayer/$GAME_ID?playerName=Sara"
curl -X GET "$BASE_URL/gameState/$GAME_ID"
echo -e "\n-----------------------------\n"

#echo "### Un solo giocatore rimane attivo (Gioco dovrebbe terminare) ###"
#curl -X POST "$BASE_URL/disconnectPlayer/$GAME_ID?playerName=Mirko"
#curl -X POST "$BASE_URL/disconnectPlayer/$GAME_ID?playerName=Sara"
#curl -X GET "$BASE_URL/gameState/$GAME_ID"
#echo -e "\n-----------------------------\n"

echo "### Fine dei test. Controlla i risultati sopra! ###"
