package org.goldratio.apns.internal

import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory
import javax.net.ssl.SSLHandshakeException

import akka.actor.{Actor, ActorLogging, Props}
import play.api.libs.json.Json

/**
 * Created by goldratio on 9/14/14.
 */

sealed trait ReconnectPolicy {
  def name: String
  def reconnected(): Unit = {

  }
}
case object NEVER extends ReconnectPolicy { val name = "NEVER" }
case object EVERY_HALF_HOUR extends ReconnectPolicy { val name = "EVERY_HALF_HOUR" }
case object EVERY_NOTIFICATION extends ReconnectPolicy { val name = "EVERY_NOTIFICATION" }

//TODO alert can be map
case class Payload(alert: String, badge: Int, sound: String = "", `content-available`: Int = 0)
case class ApnsNotification(deviceToken: String, aps: Payload, identifier: Int, expiry: Int)

trait ApnsDelegate {
  def messageSent(message: ApnsNotification)
}

class ApnsDelegateImpl extends ApnsDelegate {
  override def messageSent(message: ApnsNotification): Unit = {
    println("message send" + message.deviceToken)
  }
}

object ApnsConnection {
  val threadId = new AtomicInteger(0)

  case object Connect

  def props(factory: SocketFactory, host: String, port: Int, proxy: Option[Proxy],
            proxyUsername: Option[String], proxyPassword: Option[String], reconnectPolicy: ReconnectPolicy,
            delegate: ApnsDelegate, errorDetection: Boolean = true,
            cacheLength: Int = 0, autoAdjustCacheLength: Boolean = false, readTimeout: Int, connectTimeout: Int) =
    Props(classOf[ApnsConnection], factory, host, port, proxy, proxyUsername,
      proxyPassword, reconnectPolicy, delegate, errorDetection, cacheLength,
      autoAdjustCacheLength, readTimeout, connectTimeout)
}

class ApnsConnection(factory: SocketFactory, host: String, port: Int, proxy: Option[Proxy],
                     proxyUsername: Option[String], proxyPassword: Option[String], reconnectPolicy: ReconnectPolicy,
                     delegate: ApnsDelegate, errorDetection: Boolean, cacheLength: Int,
                     autoAdjustCacheLength: Boolean, readTimeout: Int, connectTimeout: Int) extends Actor with ActorLogging {

  var socket: Socket =  {
    val s = factory.createSocket(host, port)
    s.setSoTimeout(readTimeout)
    s.setKeepAlive(true)
    reconnectPolicy.reconnected()
    s
  }

  override def preRestart(reason : scala.Throwable, message : scala.Option[scala.Any]) = {
    message match {
      case Some(m) => self ! m
      case None =>
    }
  }

  override def receive: Actor.Receive = {
    case notification: ApnsNotification =>
      implicit val payloadFormat = Json.format[Payload]
      val aps = Json.obj("aps" -> Json.toJson(notification.aps)).toString().getBytes()
      val payload = Utilities.marshall(0, Utilities.decodeHex(notification.deviceToken), aps)
      try {
        socket.getOutputStream().write(payload)
        socket.getOutputStream().flush()
        delegate.messageSent(notification)
      }
      catch {
        case e: SSLHandshakeException =>
          log.error("connect failed", e)
          throw e
      }
  }
}
