#!/bin/bash

BASE_URL="http://localhost:8080/game"

echo "### Creazione del gioco ###"
GAME_ID=$(curl -X POST "$BASE_URL/createGame" | awk '{print $NF}')
echo "Game ID: $GAME_ID"
echo -e "\n-----------------------------\n"
echo "DEBUG - Game ID usato: $GAME_ID"

echo "### Aggiunta giocatori al gioco ###"
curl -X GET "$BASE_URL/join/$GAME_ID/Catia"
curl -X GET "$BASE_URL/join/$GAME_ID/Mirko"
curl -X GET "$BASE_URL/join/$GAME_ID/Sara"
echo -e "\n-----------------------------\n"

echo "### Avvio della partita ###"
curl -X GET "$BASE_URL/start/$GAME_ID"
echo -e "\n-----------------------------\n"

echo "### Stato iniziale della partita ###"
GAME_STATE_CATIA=$(curl -s -X GET "$BASE_URL/gameState/$GAME_ID/Catia")
echo "$GAME_STATE_CATIA"
echo -e "\n-----------------------------\n"

GAME_STATE_MIRKO=$(curl -s -X GET "$BASE_URL/gameState/$GAME_ID/Mirko")
echo "$GAME_STATE_MIRKO"
echo -e "\n-----------------------------\n"

GAME_STATE_SARA=$(curl -s -X GET "$BASE_URL/gameState/$GAME_ID/Sara")
echo "$GAME_STATE_SARA"
echo -e "\n-----------------------------\n"


extract_first_card_catia() {
    echo "$GAME_STATE_CATIA" | awk -v player="$1" '
    $0 ~ "Your hand \\(" player "\\):" {found=1; next}
    found && NF {print; exit}' | tr -d '\n'
}

extract_first_card_mirko() {
    echo "$GAME_STATE_MIRKO" | awk -v player="$1" '
    $0 ~ "Your hand \\(" player "\\):" {found=1; next}
    found && NF {print; exit}' | tr -d '\n'
}

extract_first_card_sara() {
    echo "$GAME_STATE_SARA" | awk -v player="$1" '
    $0 ~ "Your hand \\(" player "\\):" {found=1; next}
    found && NF {print; exit}' | tr -d '\n'
}


CATIA_CARD=$(extract_first_card_catia "Catia")
MIRKO_CARD=$(extract_first_card_mirko "Mirko")
SARA_CARD=$(extract_first_card_sara "Sara")

sanitize_move() {
    echo "$1" | sed 's/♥/of Coppe/g' \
               | sed 's/♠/of Spade/g' \
               | sed 's/♣/of Bastoni/g' \
               | sed 's/♦/of Denari/g' \
               | sed 's/\[//g' \
               | sed 's/\]//g' \
               | sed 's/^ *//g' | sed 's/ *$//g'
}
CATIA_CARD_CONVERTED=$(sanitize_move "$CATIA_CARD")
MIRKO_CARD_CONVERTED=$(sanitize_move "$MIRKO_CARD")
SARA_CARD_CONVERTED=$(sanitize_move "$SARA_CARD")

FIRST_CATIA_CARD=$(echo "$CATIA_CARD_CONVERTED" | awk '{print $1, $2, $3}')
FIRST_MIRKO_CARD=$(echo "$MIRKO_CARD_CONVERTED" | awk '{print $1, $2, $3}')
FIRST_SARA_CARD=$(echo "$SARA_CARD_CONVERTED" | awk '{print $1, $2, $3}')

# Codifica le carte per l'URL
CATIA_CARD_ENCODED=$(echo "$FIRST_CATIA_CARD" | sed 's/ /%20/g')
MIRKO_CARD_ENCODED=$(echo "$FIRST_MIRKO_CARD" | sed 's/ /%20/g')
SARA_CARD_ENCODED=$(echo "$FIRST_SARA_CARD" | sed 's/ /%20/g')


echo "Catia ha la carta: $CATIA_CARD_CONVERTED"
echo "Mirko ha la carta: $MIRKO_CARD_CONVERTED"
echo "Sara ha la carta: $SARA_CARD_CONVERTED"


echo "### Catia fa una mossa con la carta: $FIRST_CATIA_CARD  ###"
curl -X POST "$BASE_URL/makeMove/$GAME_ID?playerName=Catia&move=$CATIA_CARD_ENCODED"
echo -e "\n-----------------------------\n"
echo "### Verifica se i turni sono stati aggiornati correttamente ###"
curl -X GET "$BASE_URL/gameState/$GAME_ID/Catia"
echo -e "\n-----------------------------\n"


echo "### Mirko fa una mossa con la carta: $FIRST_MIRKO_CARD ###"
curl -X POST "$BASE_URL/makeMove/$GAME_ID?playerName=Mirko&move=$MIRKO_CARD_ENCODED"
echo -e "\n-----------------------------\n"
echo "### Verifica se i turni sono stati aggiornati correttamente ###"
curl -X GET "$BASE_URL/gameState/$GAME_ID/Mirko"
echo -e "\n-----------------------------\n"

echo "### Disconnessione di Sara (di turno) ###"
curl -X POST "$BASE_URL/disconnectPlayer/$GAME_ID?playerName=Sara"
curl -X GET "$BASE_URL/gameState/$GAME_ID/Sara"
echo -e "\n-----------------------------\n"

echo "### Riconnessione di Sara ###"
curl -X POST "$BASE_URL/reconnectPlayer/$GAME_ID?playerName=Sara"
curl -X GET "$BASE_URL/gameState/$GAME_ID/Sara"
echo -e "\n-----------------------------\n"


echo "### Sara fa una mossa con la carta: $FIRST_SARA_CARD ###"
curl -X POST "$BASE_URL/makeMove/$GAME_ID?playerName=Sara&move=$SARA_CARD_ENCODED"
echo -e "\n-----------------------------\n"
echo "### Verifica se i turni sono stati aggiornati correttamente ###"
curl -X GET "$BASE_URL/gameState/$GAME_ID/Sara"
echo -e "\n-----------------------------\n"


echo "### Fine del test di mosse e passaggio del turno ###"
