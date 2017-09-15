package com.github.mnogu.gatling.mqtt.action

import java.util
import java.util.Arrays.ArrayList
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

import com.github.mnogu.gatling.mqtt.protocol.MqttProtocol
import io.gatling.commons.stats.OK
import io.gatling.commons.util.ClockSingleton._
import io.gatling.commons.validation.Validation
import io.gatling.core.CoreComponents
import io.gatling.core.Predef._
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session._
import io.gatling.core.stats.message.ResponseTimings
import io.gatling.core.util.NameGen
import org.fusesource.hawtbuf.{Buffer, UTF8Buffer}
import org.fusesource.mqtt.client.{Callback, ExtendedListener, Listener, MQTT}

import scala.collection.mutable

class MqttRequestConnectAction(
  val requestName : Expression[String],
  val coreComponents : CoreComponents,
  val mqttProtocol: MqttProtocol,
  val next: Action)
   extends ExitableAction with NameGen {

  val statsEngine = coreComponents.statsEngine

  override val name = genName("mqttConnect")

  private def configureHost(session: Session)(mqtt: MQTT): Validation[MQTT] = {
    mqttProtocol.host match {
      case Some(host) => host(session).map { resolvedHost =>
        mqtt.setHost(resolvedHost)
        mqtt
      }
      case None => mqtt
    }
  }

  private def configureClientId(session: Session)(mqtt: MQTT): Validation[MQTT] = {
    mqttProtocol.optionPart.clientId match {
      case Some(clientId) => clientId(session).map { resolvedClientId =>
        mqtt.setClientId(resolvedClientId)
        mqtt
      }
      case None => mqtt
    }
  }

  private def configureUserName(session: Session)(mqtt: MQTT): Validation[MQTT] = {
    mqttProtocol.optionPart.userName match {
      case Some(userName) => userName(session).map { resolvedUserName =>
        mqtt.setUserName(resolvedUserName)
        mqtt
      }
      case None => mqtt
    }
  }

  private def configurePassword(session: Session)(mqtt: MQTT): Validation[MQTT] = {
    mqttProtocol.optionPart.password match {
      case Some(password) => password(session).map { resolvedPassword =>
        mqtt.setPassword(resolvedPassword)
        mqtt
      }
      case None => mqtt
    }
  }

  private def configureWillTopic(session: Session)(mqtt: MQTT): Validation[MQTT] = {
    mqttProtocol.optionPart.willTopic match {
      case Some(willTopic) => willTopic(session).map { resolvedWillTopic =>
        mqtt.setWillTopic(resolvedWillTopic)
        mqtt
      }
      case None => mqtt
    }
  }

  private def configureWillMessage(session: Session)(mqtt: MQTT): Validation[MQTT] = {
    mqttProtocol.optionPart.willMessage match {
      case Some(willMessage) => willMessage(session).map { resolvedWillMessage =>
        mqtt.setWillMessage(resolvedWillMessage)
        mqtt
      }
      case None => mqtt
    }
  }

  private def configureVersion(session: Session)(mqtt: MQTT): Validation[MQTT] = {
    mqttProtocol.optionPart.version match {
      case Some(version) => version(session).map { resolvedVersion =>
        mqtt.setVersion(resolvedVersion)
        mqtt
      }
      case None => mqtt
    }
  }

  private def configureOptions(mqtt: MQTT) = {
    // optionPart
    val cleanSession = mqttProtocol.optionPart.cleanSession
    if (cleanSession.isDefined) {
      mqtt.setCleanSession(cleanSession.get)
    }
    val keepAlive = mqttProtocol.optionPart.keepAlive
    if (keepAlive.isDefined) {
      mqtt.setKeepAlive(keepAlive.get)
    }
    val willQos = mqttProtocol.optionPart.willQos
    if (willQos.isDefined) {
      mqtt.setWillQos(willQos.get)
    }
    val willRetain = mqttProtocol.optionPart.willRetain
    if (willRetain.isDefined) {
      mqtt.setWillRetain(willRetain.get)
    }

    // reconnectPart
    val connectAttemptsMax = mqttProtocol.reconnectPart.connectAttemptsMax
    if (connectAttemptsMax.isDefined) {
      mqtt.setConnectAttemptsMax(connectAttemptsMax.get)
    }
    val reconnectAttemptsMax = mqttProtocol.reconnectPart.reconnectAttemptsMax
    if (reconnectAttemptsMax.isDefined) {
      mqtt.setReconnectAttemptsMax(reconnectAttemptsMax.get)
    }
    val reconnectDelay = mqttProtocol.reconnectPart.reconnectDelay
    if (reconnectDelay.isDefined) {
      mqtt.setReconnectDelay(reconnectDelay.get)
    }
    val reconnectDelayMax = mqttProtocol.reconnectPart.reconnectDelayMax
    if (reconnectDelayMax.isDefined) {
      mqtt.setReconnectDelayMax(reconnectDelayMax.get)
    }
    val reconnectBackOffMultiplier =
      mqttProtocol.reconnectPart.reconnectBackOffMultiplier
    if (reconnectBackOffMultiplier.isDefined) {
      mqtt.setReconnectBackOffMultiplier(reconnectBackOffMultiplier.get)
    }

    // socketPart
    val receiveBufferSize = mqttProtocol.socketPart.receiveBufferSize
    if (receiveBufferSize.isDefined) {
      mqtt.setReceiveBufferSize(receiveBufferSize.get)
    }
    val sendBufferSize = mqttProtocol.socketPart.sendBufferSize
    if (sendBufferSize.isDefined) {
      mqtt.setSendBufferSize(sendBufferSize.get)
    }
    val trafficClass = mqttProtocol.socketPart.trafficClass
    if (trafficClass.isDefined) {
      mqtt.setTrafficClass(trafficClass.get)
    }

    // throttlingPart
    val maxReadRate = mqttProtocol.throttlingPart.maxReadRate
    if (maxReadRate.isDefined) {
      mqtt.setMaxReadRate(maxReadRate.get)
    }
    val maxWriteRate = mqttProtocol.throttlingPart.maxWriteRate
    if (maxWriteRate.isDefined) {
      mqtt.setMaxWriteRate(maxWriteRate.get)
    }
  }

  override def execute(session: Session): Unit = recover(session) {
    val mqtt = new MQTT()

    configureHost(session)(mqtt)
      .flatMap(configureClientId(session))
      .flatMap(configureUserName(session))
      .flatMap(configurePassword(session))
      .flatMap(configureWillTopic(session))
      .flatMap(configureWillMessage(session))
      .flatMap(configureVersion(session)).map { resolvedMqtt =>

      configureOptions(resolvedMqtt)

      val connection = resolvedMqtt.callbackConnection()

      val requestStartDate = nowMillis
      val receivedMessagesBuffer = new ConcurrentHashMap[String, mutable.ArrayBuffer[String]]()

      connection.connect(new Callback[Void] {
        override def onSuccess(void: Void): Unit = {
            connection.listener(new Listener() {

              def onDisconnected(): Unit = ()

              def onConnected(): Unit = ()

              def onPublish(topic: UTF8Buffer, payload: Buffer, ack: Runnable): Unit = {
                val payloadString = new String(payload.toByteArray)
                val messages = receivedMessagesBuffer.putIfAbsent(topic.toString, mutable.ArrayBuffer(payloadString))

                if (messages != null) {
                  messages.synchronized {
                    messages.clear()
                    messages.append(new String(payload.toByteArray))
                  }
                }
                ack.run()
              }

              def onFailure(value: Throwable): Unit = {
                Thread.currentThread.getUncaughtExceptionHandler.uncaughtException(Thread.currentThread, value)
              }
            })

            next ! session.set("connection", connection).set("mqttReceivedMessages", receivedMessagesBuffer)
        }
        override def onFailure(value: Throwable): Unit = {
          connection.disconnect(null)
        }

      })
    }
  }
}
