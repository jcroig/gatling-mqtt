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
import org.fusesource.hawtbuf.UTF8Buffer
import org.fusesource.mqtt.client.{Callback, CallbackConnection, QoS, Topic}

class MqttRequestUnsubscribeAction(
  val mqttAttributes: MqttAttributes,
  val coreComponents : CoreComponents,
  val mqttProtocol: MqttProtocol,
  val next: Action)
   extends ExitableAction with NameGen {

  val statsEngine = coreComponents.statsEngine

  override val name = genName("mqttUnsubscribe")

  override def execute(session: Session): Unit = recover(session) {
    val connection = session.attributes.get("connection").get.asInstanceOf[CallbackConnection]

    mqttAttributes.requestName(session).flatMap { resolvedRequestName =>
      mqttAttributes.topic(session).map { resolvedTopic =>
        unsubscribeRequest(
          resolvedRequestName,
          connection,
          resolvedTopic,
          mqttAttributes.qos,
          session)
      }
    }
  }


  private def unsubscribeRequest(requestName: String, connection: CallbackConnection, topic: String, qos: QoS, session: Session) = {
    val requestStartDate = nowMillis

    connection.unsubscribe(Array(new UTF8Buffer(topic)), new Callback[Void] {
      override def onFailure(value: Throwable) =
        unsubscribed(isSuccess = false, Some(value.getMessage))

      override def onSuccess(value: Void) =
        unsubscribed(isSuccess = false, None)


      private def unsubscribed(isSuccess: Boolean, message: Option[String]) = {
        val requestEndDate = nowMillis

        statsEngine.logResponse(
          session,
          requestName,
          ResponseTimings(startTimestamp = requestStartDate, endTimestamp = requestEndDate),
          if (isSuccess) OK else KO,
          None,
          message
        )

        next ! session
      }
    })
  }
}
