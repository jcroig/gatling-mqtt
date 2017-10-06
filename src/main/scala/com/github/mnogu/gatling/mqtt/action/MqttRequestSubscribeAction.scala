package com.github.mnogu.gatling.mqtt.action

import com.github.mnogu.gatling.mqtt.protocol.MqttProtocol
import com.github.mnogu.gatling.mqtt.request.builder.MqttAttributes
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.ClockSingleton._
import io.gatling.core.CoreComponents
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session._
import io.gatling.core.stats.message.ResponseTimings
import io.gatling.core.util.NameGen
import org.fusesource.mqtt.client.{Callback, CallbackConnection, QoS, Topic}

class MqttRequestSubscribeAction(
  val mustSucceed: Boolean,
  val mqttAttributes: MqttAttributes,
  val coreComponents : CoreComponents,
  val mqttProtocol: MqttProtocol,
  val next: Action)
   extends ExitableAction with NameGen {

  val statsEngine = coreComponents.statsEngine

  override val name = genName("mqttSubscribe")

  override def execute(session: Session): Unit = recover(session) {
    val connection = session.attributes.get("connection").get.asInstanceOf[CallbackConnection]

    mqttAttributes.requestName(session).flatMap { resolvedRequestName =>
      mqttAttributes.topic(session).map { resolvedTopic =>
        subscribeRequest(
          resolvedRequestName,
          connection,
          resolvedTopic,
          mqttAttributes.qos,
          session)
      }
    }
  }


  private def subscribeRequest(requestName: String, connection: CallbackConnection, topic: String, qos: QoS, session: Session) = {
    val requestStartDate = nowMillis

    connection.subscribe(Array(new Topic(topic, qos)), new Callback[Array[Byte]] {
      override def onFailure(value: Throwable) =
        subscribed(isSuccess = false, Some(value.getMessage))

      override def onSuccess(value: Array[Byte]) =
        subscribed(isSuccess = true, None)

      private def subscribed(isSuccess: Boolean, message: Option[String]) = {
        val requestEndDate = nowMillis
        val success = if ((isSuccess && mustSucceed) || (!isSuccess && !mustSucceed)) OK else KO

        statsEngine.logResponse(
          session,
          requestName,
          ResponseTimings(startTimestamp = requestStartDate, endTimestamp = requestEndDate),
          success,
          None,
          if (success == OK) None else message.orElse(Some("Subscribe expected to fail but it didn't"))
        )

        next ! (if (success == OK) session else session.markAsFailed)
      }
    })
  }
}
