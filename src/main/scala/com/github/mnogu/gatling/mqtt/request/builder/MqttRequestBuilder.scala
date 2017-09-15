package com.github.mnogu.gatling.mqtt.request.builder

import com.github.mnogu.gatling.mqtt.action._
import io.gatling.commons.validation.Validation
import io.gatling.core.session.Expression
import org.fusesource.mqtt.client.QoS
import io.gatling.core.session._

case class MqttAttributes(
  requestName: Expression[String],
  topic: Expression[String],
  payload: Expression[String],
  qos: QoS,
  retain: Boolean)

case class MqttRequestBuilder(requestName: Expression[String]) {
  def publish(
    topic: Expression[String],
    payload: Expression[String],
    qos: QoS,
    retain: Boolean): MqttRequestPublishActionBuilder =
    new MqttRequestPublishActionBuilder(MqttAttributes(
      requestName,
      topic,
      payload,
      qos,
      retain))

  def subscribe(topic: Expression[String], qoS: QoS): MqttRequestSubscribeActionBuilder =
    new MqttRequestSubscribeActionBuilder(MqttAttributes(requestName, topic, "".expressionSuccess, qoS, false))

  def unsubscribe(topic: Expression[String]): MqttRequestUnsubscribeActionBuilder =
    new MqttRequestUnsubscribeActionBuilder(MqttAttributes(requestName, topic, "".expressionFailure, QoS.AT_LEAST_ONCE, false))

  def readMessage(topic: Expression[String])(handler: (String, Session) => Validation[Session]): MqttRequestReadMessageActionBuilder =
    new MqttRequestReadMessageActionBuilder(requestName, topic)(handler)

  def connect(): MqttRequestConnectActionBuilder = new MqttRequestConnectActionBuilder(requestName)
  def disconnect(): MqttRequestDisconnectActionBuilder = new MqttRequestDisconnectActionBuilder()
}
