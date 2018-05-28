package com.lightbend.akka.iot

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import com.lightbend.akka.iot.DeviceManager.RequestTrackDevice

import scala.concurrent.duration._

object DeviceGroup {
  def props(groupId: String): Props = Props(new DeviceGroup(groupId))

  final case class RequestDeviceList(requestId: Long)

  final case class ReplyDeviceList(requestId: Long, ids: Set[String])

  sealed trait TemperatureReading

  final case class Temperature(value: Double) extends TemperatureReading

  case object TemperatureNotAvailable extends TemperatureReading

  case object DeviceNotAvailable extends TemperatureReading

  case object DeviceTimedOut extends TemperatureReading

  final case class RequestAllTemperatures(requestId: Long)

  final case class RespondAllTemperatures(requestId: Long, temperatures: Map[String, TemperatureReading])

}

class DeviceGroup(groupId: String) extends Actor with ActorLogging {

  import DeviceGroup._

  override def preStart(): Unit = log.info("DeviceGroup {} started", groupId)

  override def postStop(): Unit = log.info("DeviceGroup {} stopped", groupId)

  override def receive: Receive = {
    receiveWithDevices(Map.empty[String, ActorRef], Map.empty[ActorRef, String])
  }

  private def receiveWithDevices(deviceIdToActor: Map[String, ActorRef],
                                 actorToDeviceId: Map[ActorRef, String]): Receive = {
    case trackMsg@RequestTrackDevice(`groupId`, _) ⇒
      deviceIdToActor.get(trackMsg.deviceId) match {
        case Some(deviceActor) ⇒
          deviceActor forward trackMsg
        case None ⇒
          log.info("Creating device actor for {}", trackMsg.deviceId)
          val deviceActor: ActorRef = context.actorOf(Device.props(groupId, trackMsg.deviceId), s"device-${trackMsg.deviceId}")
          context.watch(deviceActor)

          context.become(receiveWithDevices(
            deviceIdToActor + (trackMsg.deviceId -> deviceActor),
            actorToDeviceId + (deviceActor -> trackMsg.deviceId)
          ))

          deviceActor forward trackMsg
      }
    case RequestAllTemperatures(requestId) ⇒
      context.actorOf(DeviceGroupQuery.props(
        actorToDeviceId = actorToDeviceId,
        requestId = requestId,
        requester = sender(),
        3.seconds
      ))
    case RequestTrackDevice(group, _) ⇒
      log.warning(
        "Ignoring TrackDevice request for {}. This actor is responsible for {}.",
        group, this.groupId
      )
    // To make it testable, we add a new query capability
    case RequestDeviceList(requestId) ⇒
      sender() ! ReplyDeviceList(requestId, deviceIdToActor.keySet)
    case Terminated(deviceActor) ⇒
      val deviceId = actorToDeviceId(deviceActor)
      log.info("Device actor for {} has been terminated", deviceId)
      context.become(receiveWithDevices(
        deviceIdToActor - deviceId,
        actorToDeviceId - deviceActor
      ))
  }
}
