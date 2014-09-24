package org.goldratio.apns

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.{ Config, ConfigFactory }
import org.goldratio.apns.internal.{ ApnsConnectionSupervisor, ApnsDelegateImpl, ApnsNotification, Payload }

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Created by goldratio on 9/22/14.
 */
object NotificationProcessor {
  def props() = Props(classOf[NotificationProcessor])
}

case class Message(deviceToken: String, message: String, badge: Int)

class NotificationProcessor extends Actor with ActorLogging {
  implicit val timeout = Timeout(120, TimeUnit.SECONDS)

  val config = ConfigFactory.load().getConfig("campfire.apns")
  val Settings = new Settings(config)
  class Settings(config: Config) {
    val host = config.getString("server.host")
    val port = config.getInt("server.port")
    val cert = config.getString("cert")
    val password = config.getString("password")
  }

  var apns: Option[ActorRef] = None
  val delegate = new ApnsDelegateImpl()

  val supervisor = context.actorOf(Props[ApnsConnectionSupervisor], "apns-supervisor")

  override def preStart() = {
    val f = supervisor ? ApnsService.props(Settings.cert, Settings.password, Settings.host, Settings.port, delegate)
    val a = Await.result(f, 60 seconds).asInstanceOf[ActorRef]
    apns = Some(a)
  }

  //val apns = context.actorOf(ApnsService.props(Settings.cert, Settings.password, Settings.host, Settings.port, delegate))
  override def receive: Receive = {
    case message: Message =>
      val payload = Payload(message.message, message.badge)
      apns.map { apns =>
        apns ! ApnsNotification(message.deviceToken, payload, 1, 3)
      }
  }

}
object Test extends App {

  implicit val timeout = Timeout(120, TimeUnit.SECONDS)
  implicit val system = ActorSystem()
  val test = system.actorOf(NotificationProcessor.props, "apns")
  Thread.sleep(60000)
  (1 to 20).foreach { i =>
    val message = s"${i} st message"
    test ! Message("04a77581 8fb3de3b 3cec9b94 353a2168 a77f879c ea26fb9c b4d1f36e 193b6d24", message, i)
  }
}
