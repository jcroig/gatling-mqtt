package com.github.mnogu.gatling.mqtt.action

import java.util.concurrent.ConcurrentMap

import scala.collection.mutable

class MQttState(var connected: Boolean, val receivedMessages: ConcurrentMap[String, mutable.ArrayBuffer[String]])