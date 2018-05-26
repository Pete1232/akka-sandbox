package com.lightbend.akka.iot

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

class DeviceGroupSpec extends TestKit(ActorSystem("DeviceGroupSpec"))
  with WordSpecLike with MustMatchers with BeforeAndAfterAll {

  override def afterAll: Unit = {
    shutdown(system)
  }

  "a device group" when {
    "performing a group query" must {
      "be able to collect temperatures from all active devices" in {
        val probe = TestProbe()
        val groupActor: ActorRef = system.actorOf(DeviceGroup.props("group"))

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device1"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)
        val deviceActor1: ActorRef = probe.lastSender

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device2"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)
        val deviceActor2: ActorRef = probe.lastSender

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device3"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)
        val deviceActor3: ActorRef = probe.lastSender

        // Check that the device actors are working
        deviceActor1.tell(Device.RecordTemperature(requestId = 0, 1.0), probe.ref)
        probe.expectMsg(Device.TemperatureRecorded(requestId = 0))
        deviceActor2.tell(Device.RecordTemperature(requestId = 1, 2.0), probe.ref)
        probe.expectMsg(Device.TemperatureRecorded(requestId = 1))
        // No temperature for device3

        groupActor.tell(DeviceGroup.RequestAllTemperatures(requestId = 0), probe.ref)
        probe.expectMsg(
          DeviceGroup.RespondAllTemperatures(
            requestId = 0,
            temperatures = Map(
              "device1" -> DeviceGroup.Temperature(1.0),
              "device2" -> DeviceGroup.Temperature(2.0),
              "device3" -> DeviceGroup.TemperatureNotAvailable)))
      }
    }
    "registering a new device for tracking" must {
      "be able to register a device actor" in {
        val probe = TestProbe()
        val groupActor: ActorRef = system.actorOf(DeviceGroup.props("group"))

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device1"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)
        val deviceActor1: ActorRef = probe.lastSender

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device2"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)
        val deviceActor2: ActorRef = probe.lastSender
        deviceActor1 must not be deviceActor2

        // Check that the device actors are working
        deviceActor1.tell(Device.RecordTemperature(requestId = 0, 1.0), probe.ref)
        probe.expectMsg(Device.TemperatureRecorded(requestId = 0))
        deviceActor2.tell(Device.RecordTemperature(requestId = 1, 2.0), probe.ref)
        probe.expectMsg(Device.TemperatureRecorded(requestId = 1))
      }
      "ignore requests for wrong groupId" in {
        val probe = TestProbe()
        val groupActor: ActorRef = system.actorOf(DeviceGroup.props("group"))

        groupActor.tell(DeviceManager.RequestTrackDevice("wrongGroup", "device1"), probe.ref)
        probe.expectNoMessage()
      }
      "return same actor for same deviceId" in {
        val probe = TestProbe()
        val groupActor: ActorRef = system.actorOf(DeviceGroup.props("group"))

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device1"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)
        val deviceActor1: ActorRef = probe.lastSender

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device1"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)
        val deviceActor2: ActorRef = probe.lastSender

        deviceActor1 mustBe deviceActor2
      }
      "be able to list active devices" in {
        val probe = TestProbe()
        val groupActor: ActorRef = system.actorOf(DeviceGroup.props("group"))

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device1"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device2"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)

        groupActor.tell(DeviceGroup.RequestDeviceList(requestId = 0), probe.ref)
        probe.expectMsg(DeviceGroup.ReplyDeviceList(requestId = 0, Set("device1", "device2")))
      }
      "be able to list active devices after one shuts down" in {
        val probe = TestProbe()
        val groupActor: ActorRef = system.actorOf(DeviceGroup.props("group"))

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device1"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)
        val toShutDown: ActorRef = probe.lastSender

        groupActor.tell(DeviceManager.RequestTrackDevice("group", "device2"), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)

        groupActor.tell(DeviceGroup.RequestDeviceList(requestId = 0), probe.ref)
        probe.expectMsg(DeviceGroup.ReplyDeviceList(requestId = 0, Set("device1", "device2")))

        probe.watch(toShutDown)
        toShutDown ! PoisonPill
        probe.expectTerminated(toShutDown)

        // using awaitAssert to retry because it might take longer for the groupActor
        // to see the Terminated, that order is undefined
        probe.awaitAssert {
          groupActor.tell(DeviceGroup.RequestDeviceList(requestId = 1), probe.ref)
          probe.expectMsg(DeviceGroup.ReplyDeviceList(requestId = 1, Set("device2")))
        }
      }
    }
  }
}
