package com.lightbend.akka.iot

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

class DeviceManagerSpec extends TestKit(ActorSystem("DeviceManagerSpec"))
  with WordSpecLike with MustMatchers with BeforeAndAfterAll {

  override def afterAll: Unit = {
    shutdown(system)
  }

  "a device manager" when {
    "registering a new device for tracking" must {
      "be able to register a device actor" in {
        val probe = TestProbe()
        val managerActor: ActorRef = system.actorOf(DeviceManager.props())

        managerActor.tell(DeviceManager.RequestTrackDevice("group1", "device1"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)
        val groupActor1: ActorRef = probe.lastSender

        managerActor.tell(DeviceManager.RequestTrackDevice("group2", "device2"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)
        val groupActor2: ActorRef = probe.lastSender
        groupActor1 must not be groupActor2

        // Check that the device actors are working
        groupActor1.tell(Device.RecordTemperature(requestId = 0, 1.0), probe.ref)
        probe.expectMsg(Device.TemperatureRecorded(requestId = 0))
        groupActor2.tell(Device.RecordTemperature(requestId = 1, 2.0), probe.ref)
        probe.expectMsg(Device.TemperatureRecorded(requestId = 1))
      }
      "return same actor for same groupId and deviceId" in {
        val probe = TestProbe()
        val managerActor: ActorRef = system.actorOf(DeviceManager.props())

        managerActor.tell(DeviceManager.RequestTrackDevice("group1", "device1"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)
        val groupActor1: ActorRef = probe.lastSender

        managerActor.tell(DeviceManager.RequestTrackDevice("group1", "device1"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)
        val groupActor2: ActorRef = probe.lastSender

        groupActor1 mustBe groupActor2
      }
      "be able to list active groups" in {
        val probe = TestProbe()
        val managerActor: ActorRef = system.actorOf(DeviceManager.props())

        managerActor.tell(DeviceManager.RequestTrackDevice("group1", "device1"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)

        managerActor.tell(DeviceManager.RequestTrackDevice("group2", "device2"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)

        managerActor.tell(DeviceManager.RequestGroupList(requestId = 0), probe.ref)
        probe.expectMsg(DeviceManager.ReplyGroupList(requestId = 0, Set("group1", "group2")))
      }
      "be able to return an actor for a group" in {
        val probe = TestProbe()
        val managerActor: ActorRef = system.actorOf(DeviceManager.props())

        managerActor.tell(DeviceManager.RequestTrackDevice("group1", "device1"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)

        managerActor.tell(DeviceManager.RequestTrackDevice("group2", "device2"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)

        managerActor.tell(DeviceManager.RequestGroupList(requestId = 0), probe.ref)
        probe.expectMsg(DeviceManager.ReplyGroupList(requestId = 0, Set("group1", "group2")))

        managerActor.tell(DeviceManager.RequestGroupActor(requestId = 0, "group1"), probe.ref)
        val response: DeviceManager.ReplyGroupActor = probe.expectMsgType[DeviceManager.ReplyGroupActor]

        response.ref.tell(DeviceGroup.RequestDeviceList(requestId = 0), probe.ref)
        probe.expectMsg(DeviceGroup.ReplyDeviceList(requestId = 0, Set("device1")))
      }
      "be able to list active groups after one shuts down" in {
        val probe = TestProbe()
        val managerActor: ActorRef = system.actorOf(DeviceManager.props())

        managerActor.tell(DeviceManager.RequestTrackDevice("group1", "device1"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)

        managerActor.tell(DeviceManager.RequestGroupActor(requestId = 0, "group1"), probe.ref)
        val groupToShutdown: DeviceManager.ReplyGroupActor = probe.expectMsgType[DeviceManager.ReplyGroupActor]
        val toShutDown: ActorRef = groupToShutdown.ref

        managerActor.tell(DeviceManager.RequestTrackDevice("group2", "device2"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)

        managerActor.tell(DeviceManager.RequestGroupList(requestId = 0), probe.ref)
        probe.expectMsg(DeviceManager.ReplyGroupList(requestId = 0, Set("group1", "group2")))

        probe.watch(toShutDown)
        toShutDown ! PoisonPill
        probe.expectTerminated(toShutDown)

        // using awaitAssert to retry because it might take longer for the groupActor
        // to see the Terminated, that order is undefined
        probe.awaitAssert {
          managerActor.tell(DeviceManager.RequestGroupList(requestId = 1), probe.ref)
          probe.expectMsg(DeviceManager.ReplyGroupList(requestId = 1, Set("group2")))
        }
      }
    }
  }
}
