package com.github.mnogu.gatling.mqtt.action

import com.github.mnogu.gatling.mqtt.protocol.{MqttComponents, MqttProtocol}
import io.gatling.commons.validation.Validation
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.structure.ScenarioContext

class MqttRequestReadMessageActionBuilder(requestName: Expression[String],
                                          topic: Expression[String])(handler: (Option[String], Session) => Validation[Session]) extends ActionBuilder {

  override def build(
    ctx: ScenarioContext, next: Action
  ): Action = {
    import ctx._

    val mqttComponents : MqttComponents = protocolComponentsRegistry.components(MqttProtocol.MqttProtocolKey)
    
    new MqttRequestReadMessageAction(
      requestName,
      topic,
      handler,
      coreComponents,
      mqttComponents.mqttProtocol,
      next
    )
  }
}
