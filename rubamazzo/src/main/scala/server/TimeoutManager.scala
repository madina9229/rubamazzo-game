package server

import akka.actor.{ActorSystem, Cancellable}
import akka.event.Logging
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

object TimeoutManager {

  private val system = ActorSystem("TimeoutManager")
  private val log = Logging(system, getClass)

  // Map to track the last action timestamp of each player
  private val lastActionTime = TrieMap[String, Long]()
  private var cancellables = TrieMap[String, Cancellable]()

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  /**
   * Records a player's action by updating their last action timestamp.
   * If the player is in the system timeout, cancel the timeout.
   * @param playerName Name of the player.
   */
  def recordAction(playerName: String): Unit = {
    val currentTime = System.currentTimeMillis()
    lastActionTime.put(playerName, currentTime)
    // Cancel timeout only if it exists
    if (cancellables.contains(playerName)) {
      removePlayer(playerName)
    }
    log.info(s"[Timeout Manager] Updated last action for player $playerName at $currentTime.")
  }

  /**
   * Schedules a timeout for a player. If the timeout expires without new actions,
   * executes the provided `onTimeout` action.
   * @param playerName Name of the player.
   * @param timeoutDuration Timeout duration in milliseconds.
   * @param onTimeout Action to execute upon timeout.
   */
  def scheduleTimeout(playerName: String, timeoutDuration: Long)(onTimeout: => Unit): Unit = {
    // Cancel any previous timeout for the player
    cancellables.get(playerName).foreach(_.cancel())
    log.info(s"[Timeout Manager] Scheduled timeout for player $playerName (Duration: $timeoutDuration ms).")
    // Schedule a new timeout
    val cancellable = system.scheduler.scheduleOnce(timeoutDuration.milliseconds) {
      val lastAction = lastActionTime.getOrElse(playerName, 0L)
      if (System.currentTimeMillis() - lastAction >= timeoutDuration) {
        log.info(s"[Timeout Manager] Timeout reached for player $playerName.")
        onTimeout
      }
    }

    cancellables.put(playerName, cancellable)
  }

  /**
   * Removes a player from timeout management, clearing the schedule.
   * @param playerName Name of the player.
   */
  def removePlayer(playerName: String): Unit = {
    cancellables.remove(playerName).foreach(_.cancel())

    // Remove last action only if player had an active timeout
    if (lastActionTime.contains(playerName)) {
      lastActionTime.remove(playerName)
      log.info(s"[Timeout Manager] Removed player $playerName from tracking.")
    }
  }

  /**
   * Retrieves the last action timestamp for a player.
   * @param playerName Name of the player.
   * @return Timestamp of the last action, or None if not found.
   */
  def getLastAction(playerName: String): Option[Long] = {
    lastActionTime.get(playerName)
  }
}
