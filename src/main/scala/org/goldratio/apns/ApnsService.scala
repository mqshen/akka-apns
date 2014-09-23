package org.goldratio.apns

import java.io.FileInputStream

import akka.actor._
import org.goldratio.apns.internal.Utilities._
import org.goldratio.apns.internal._

/**
 * Created by goldratio on 9/14/14.
 */

object ApnsService {
  val KEYSTORE_TYPE = "PKCS12"
  val KEY_ALGORITHM = if (java.security.Security.getProperty("ssl.KeyManagerFactory.algorithm") == null) "sunx509" else
    java.security.Security.getProperty("ssl.KeyManagerFactory.algorithm")

  def props(fileName: String, password: String, host: String, port: Int, delegate: ApnsDelegate,
            proxy: Option[Proxy] = None, readTimeout: Int = 0, connectTimeout: Int = 0,
            proxyUsername: Option[String] = None, proxyPassword: Option[String] = None) =
    Props(classOf[ApnsService], fileName, password, host, port, delegate, proxy,
      readTimeout, connectTimeout, proxyUsername, proxyPassword)

}

class ApnsService(
    fileName: String, password: String, host: String, port: Int, delegate: ApnsDelegate,
    proxy: Option[Proxy] = None, readTimeout: Int = 0, connectTimeout: Int = 0,
    proxyUsername: Option[String] = None, proxyPassword: Option[String] = None) extends Actor {

  import org.goldratio.apns.ApnsService._

  val conn = {
    val stream = new FileInputStream(fileName)
    val sslContext = newSSLContext(stream, password, KEYSTORE_TYPE, KEY_ALGORITHM)
    val sslFactory = sslContext.getSocketFactory()

    val feedback = ApnsFeedbackConnection(sslFactory, host, port, proxy, readTimeout, connectTimeout, proxyUsername, proxyPassword)

    context.actorOf(ApnsConnection.props(sslFactory, host, port, proxy, proxyUsername, proxyPassword,
      EVERY_NOTIFICATION, delegate, readTimeout = readTimeout, connectTimeout = connectTimeout))
  }

  override def receive: Receive = {
    case msg: ApnsNotification =>
      conn ! msg
  }

}
