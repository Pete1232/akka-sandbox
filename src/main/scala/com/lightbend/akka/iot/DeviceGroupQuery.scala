package com.lightbend.akka.iot

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props, Terminated}
import com.lightbend.akka.iot.DeviceGroup.TemperatureReading

import scala.concurrent.duration.FiniteDuration

object DeviceGroupQuery {

  case object CollectionTimeout

  def props(
             actorToDeviceId: Map[ActorRef, String],
             requestId: Long,
             requester: ActorRef,
             timeout: FiniteDuration
           ): Props = {
    Props(new DeviceGroupQuery(actorToDeviceId, requestId, requester, timeout))
  }
}

class DeviceGroupQuery(actorToDeviceId: Map[ActorRef, String],
                       requestId: Long,
                       requester: ActorRef,
                       timeout: FiniteDuration
                      ) extends Actor with ActorLogging {

  import DeviceGroupQuery._
  import context.dispatcher

  val queryTimeoutTimer: Cancellable = context.system.scheduler.scheduleOnce(timeout, self, CollectionTimeout)

  override def preStart(): Unit = {
    actorToDeviceId.keysIterator.foreach { deviceActor ⇒
      context.watch(deviceActor)
      deviceActor ! Device.ReadTemperature(0)
    }
  }

  override def postStop(): Unit = {
    queryTimeoutTimer.cancel()
  }

  override def receive: Receive =
    waitingForReplies(
      Map.empty,
      actorToDeviceId.keySet
    )

  def receivedResponse(deviceActor: ActorRef,
                       reading: DeviceGroup.TemperatureReading,
                       stillWaiting: Set[ActorRef],
                       repliesSoFar: Map[String, DeviceGroup.TemperatureReading]
                      ): Unit = {
    context.unwatch(deviceActor)
    val deviceId = actorToDeviceId(deviceActor)
    val newStillWaiting: Set[ActorRef] = stillWaiting - deviceActor

    val newRepliesSoFar: Map[String, TemperatureReading] = repliesSoFar + (deviceId -> reading)
    if (newStillWaiting.isEmpty) {
      requester ! DeviceGroup.RespondAllTemperatures(requestId, newRepliesSoFar)
      context.stop(self)
    } else {
      context.become(waitingForReplies(newRepliesSoFar, newStillWaiting))
    }
  }

  def waitingForReplies(repliesSoFar: Map[String, DeviceGroup.TemperatureReading],
                        stillWaiting: Set[ActorRef]
                       ): Receive = {
    case Device.RespondTemperature(0, valueOption) ⇒
      val deviceActor: ActorRef = sender()
      val reading: TemperatureReading = valueOption match {
        case Some(value) ⇒ DeviceGroup.Temperature(value)
        case None ⇒ DeviceGroup.TemperatureNotAvailable
      }
      receivedResponse(deviceActor, reading, stillWaiting, repliesSoFar)

    case Terminated(deviceActor) ⇒
      receivedResponse(deviceActor, DeviceGroup.DeviceNotAvailable, stillWaiting, repliesSoFar)

    case CollectionTimeout ⇒
      val timedOutReplies: Set[(String, TemperatureReading)] =
        stillWaiting.map { deviceActor ⇒
          val deviceId = actorToDeviceId(deviceActor)
          deviceId -> DeviceGroup.DeviceTimedOut
        }
      requester ! DeviceGroup.RespondAllTemperatures(requestId, repliesSoFar ++ timedOutReplies)
      context.stop(self)
  }
}
