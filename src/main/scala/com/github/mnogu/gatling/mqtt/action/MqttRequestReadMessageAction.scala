package com.github.mnogu.gatling.mqtt.action

import com.github.mnogu.gatling.mqtt.protocol.MqttProtocol
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.ClockSingleton._
import io.gatling.commons.validation.{Failure, Success, Validation}
import io.gatling.core.CoreComponents
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session._
import io.gatling.core.stats.message.ResponseTimings
import io.gatling.core.util.NameGen

class MqttRequestReadMessageAction(val requestName: Expression[String],
                                   val topic: Expression[String],
                                   val handler: (Option[String], Session) => Validation[Session],
                                   val coreComponents : CoreComponents,
                                   val mqttProtocol: MqttProtocol,
                                   val next: Action)
   extends ExitableAction with NameGen {

  val statsEngine = coreComponents.statsEngine

  override val name = genName("mqttReadMessage")

  override def execute(session: Session): Unit = recover(session) {
    requestName(session).flatMap { resolvedRequestName =>
      topic(session).map { resolvedTopic =>
        readMessage(resolvedRequestName, resolvedTopic, handler, session)
      }
    }
  }


  private def readMessage(requestName: String,
                          topic: String,
                          handler: (Option[String], Session) => Validation[Session],
                          session: Session) = {

    val requestStartDate = nowMillis
    val mqttState = session("mqttState").asOption[MQttState]

    def logStats(isSuccess: Boolean, endTimestamp: Long, message: Option[String]) =
      statsEngine.logResponse(
        session,
        requestName,
        ResponseTimings(requestStartDate, endTimestamp),
        if (isSuccess) OK else KO,
        None,
        message
      )


    val lastMessage = mqttState.flatMap(m => Option(m.receivedMessages.get(topic))).flatMap(_.headOption)

    handler(lastMessage, session) match {
      case Success(newSession) =>
        logStats(true, nowMillis, None)
        next ! newSession

      case Failure(message) =>
        logStats(false, nowMillis, Some(s"Receive message handler check failed for message in topic $topic: $message"))
        next ! session.markAsFailed
    }
  }
}