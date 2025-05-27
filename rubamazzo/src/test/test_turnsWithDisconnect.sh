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
GAME_STATE=$(curl -s -X GET "$BASE_URL/gameState/$GAME_ID")
echo "$GAME_STATE"
echo -e "\n-----------------------------\n"

CATIA_CARD=$(echo "$GAME_STATE" | jq -r '.playerHands.Catia[0]')
MIRKO_CARD=$(echo "$GAME_STATE" | jq -r '.playerHands.Mirko[0]')
SARA_CARD=$(echo "$GAME_STATE" | jq -r '.playerHands.Sara[0]')

# Codifica le carte per l'URL
CATIA_CARD_ENCODED=$(echo "$CATIA_CARD" | sed 's/ /%20/g')
MIRKO_CARD_ENCODED=$(echo "$MIRKO_CARD" | sed 's/ /%20/g')
SARA_CARD_ENCODED=$(echo "$SARA_CARD" | sed 's/ /%20/g')

echo "Catia ha la carta: $CATIA_CARD"
echo "Mirko ha la carta: $MIRKO_CARD"
echo "Sara ha la carta: $SARA_CARD"


echo "### Catia fa una mossa con la carta: $CATIA_CARD ###"
curl -X POST "$BASE_URL/makeMove/$GAME_ID?playerName=Catia&move=$CATIA_CARD_ENCODED"
echo -e "\n-----------------------------\n"
echo "### Verifica se i turni sono stati aggiornati correttamente ###"
curl -X GET "$BASE_URL/gameState/$GAME_ID"
echo -e "\n-----------------------------\n"

echo "### Disconnessione di Mirko (di turno) ###"
curl -X POST "$BASE_URL/disconnectPlayer/$GAME_ID?playerName=Mirko"
curl -X GET "$BASE_URL/gameState/$GAME_ID"
echo -e "\n-----------------------------\n"

echo "### Riconnessione di Mirko ###"
curl -X POST "$BASE_URL/reconnectPlayer/$GAME_ID?playerName=Mirko"
curl -X GET "$BASE_URL/gameState/$GAME_ID"
echo -e "\n-----------------------------\n"

echo "### Mirko fa una mossa con la carta: $MIRKO_CARD ###"
curl -X POST "$BASE_URL/makeMove/$GAME_ID?playerName=Mirko&move=$MIRKO_CARD_ENCODED"
echo -e "\n-----------------------------\n"
echo "### Verifica se i turni sono stati aggiornati correttamente ###"
curl -X GET "$BASE_URL/gameState/$GAME_ID"
echo -e "\n-----------------------------\n"

echo "### Sara fa una mossa con la carta: $SARA_CARD ###"
curl -X POST "$BASE_URL/makeMove/$GAME_ID?playerName=Sara&move=$SARA_CARD_ENCODED"
echo -e "\n-----------------------------\n"
echo "### Verifica se i turni sono stati aggiornati correttamente ###"
curl -X GET "$BASE_URL/gameState/$GAME_ID"
echo -e "\n-----------------------------\n"


echo "### Fine del test di mosse e passaggio del turno ###"
