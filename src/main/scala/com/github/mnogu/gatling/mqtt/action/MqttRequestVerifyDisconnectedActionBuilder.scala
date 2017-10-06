package com.github.mnogu.gatling.mqtt.action

import com.github.mnogu.gatling.mqtt.protocol.{MqttComponents, MqttProtocol}
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session._
import io.gatling.core.structure.ScenarioContext

class MqttRequestVerifyDisconnectedActionBuilder(requestName: Expression[String])
  extends ActionBuilder {

  override def build(
                      ctx: ScenarioContext, next: Action
                    ): Action = {
    import ctx._

    val mqttComponents: MqttComponents = protocolComponentsRegistry.components(MqttProtocol.MqttProtocolKey)

    new MqttRequestVerifyDisconnectedAction(
      requestName,
      coreComponents,
      mqttComponents.mqttProtocol,
      next
    )
  }
}
