package com.lightbend.akka.iot

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}

object DeviceManager {
  def props(): Props = Props(new DeviceManager)

  final case class RequestTrackDevice(groupId: String, deviceId: String)

  case object DeviceRegistered

  final case class RequestGroupList(requestId: Long)

  final case class ReplyGroupList(requestId: Long, ids: Set[String])

  final case class RequestGroupActor(requestId: Long, groupId: String)

  final case class ReplyGroupActor(requestId: Long, ref: ActorRef)

}

class DeviceManager extends Actor with ActorLogging {

  import DeviceManager._

  override def preStart(): Unit = log.info("DeviceManager started")

  override def postStop(): Unit = log.info("DeviceManager stopped")

  override def receive: Receive = {
    receiveWithDeviceGroups(Map.empty, Map.empty)
  }

  private def receiveWithDeviceGroups(groupIdToActor: Map[String, ActorRef],
                                      actorToGroupId: Map[ActorRef, String]): Receive = {
    case trackMsg@RequestTrackDevice(groupId, _) ⇒
      groupIdToActor.get(groupId) match {
        case Some(ref) ⇒
          ref forward trackMsg
        case None ⇒
          log.info("Creating device group actor for {}", groupId)
          val groupActor: ActorRef = context.actorOf(DeviceGroup.props(groupId), "group-" + groupId)
          context.watch(groupActor)

          context.become(receiveWithDeviceGroups(
            groupIdToActor + (groupId -> groupActor),
            actorToGroupId + (groupActor -> groupId)
          ))

          groupActor forward trackMsg
      }
    case RequestGroupList(requestId) ⇒
      sender() ! ReplyGroupList(requestId, groupIdToActor.keySet)
    case RequestGroupActor(requestId, groupId) ⇒
      sender() ! ReplyGroupActor(requestId, groupIdToActor(groupId))
    case Terminated(groupActor) ⇒
      val groupId = actorToGroupId(groupActor)
      log.info("Device group actor for {} has been terminated", groupId)

      context.become(receiveWithDeviceGroups(
        groupIdToActor - groupId,
        actorToGroupId - groupActor
      ))
  }
}
