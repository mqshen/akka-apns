package org.goldratio.apns.internal

import javax.net.ssl.SSLHandshakeException

import akka.actor.{Actor, Props}
import akka.routing.FromConfig
import com.typesafe.config.{Config, ConfigFactory}

/**
 * Created by goldratio on 9/22/14.
 */
class ApnsConnectionSupervisor extends Actor {

  import akka.actor.OneForOneStrategy
  import akka.actor.SupervisorStrategy._

import scala.concurrent.duration._

  val config = ConfigFactory.load().getConfig("campfire.apns")
  val Settings = new Settings(config)
  class Settings(config: Config) {
    val parallel = config.getBoolean("server.parallel")
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case _: ArithmeticException      => Resume
      case _: SSLHandshakeException     => Resume
      case _: IllegalArgumentException => Stop
    }

  def receive = {
    case p: Props => {
      if(Settings.parallel)
        sender() ! context.actorOf(p.withRouter(FromConfig()), "apns-router")
      else
        sender() ! context.actorOf(p)
    }
  }

}
