package com.github.mnogu.gatling.mqtt.action

import com.github.mnogu.gatling.mqtt.protocol.MqttProtocol
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.ClockSingleton._
import io.gatling.core.CoreComponents
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session._
import io.gatling.core.stats.message.ResponseTimings
import io.gatling.core.util.NameGen

class MqttRequestVerifyDisconnectedAction(
  val requestName : Expression[String],
  val coreComponents : CoreComponents,
  val mqttProtocol: MqttProtocol,
  val next: Action)
   extends ExitableAction with NameGen {

  val statsEngine = coreComponents.statsEngine

  override val name = genName("mqttVerifyDisconnected")

  override def execute(session: Session): Unit = recover(session) {

    for {
      reqName <- requestName(session)
    } yield {
      val requestStartDate = nowMillis
      val mqttState = session("mqttState").asOption[MQttState]

      if (!mqttState.exists(_.connected)) {
        statsEngine.logResponse(
          session,
          reqName,
          ResponseTimings(requestStartDate, nowMillis),
          OK,
          None,
          None
        )
        next ! session
      } else {
        statsEngine.logResponse(
          session,
          reqName,
          ResponseTimings(requestStartDate, nowMillis),
          KO,
          None,
          Some(s"Client is connected")
        )
        next ! session.markAsFailed
      }
    }
  }
}