package utils

object Utils {

  def suitToSymbol(suit: String): String = suit match {
    case "Spade" => "\u2660"
    case "Denari" => "\u2666"
    case "Bastoni" => "\u2663"
    case "Coppe" => "\u2665"
    case _ => suit
  }


  def displayCard(card: String): String = {
    val parts = card.split(" of ")
    if (parts.length < 2) return card

    val rawRank = parts(0)
    val rawSuit = parts(1)

    val suitSymbol = suitToSymbol(rawSuit)

    s"[$rawRank $suitSymbol]"
  }

}
