package com.lightbend.akka.iot

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

class DeviceSpec extends TestKit(ActorSystem("DeviceSpec"))
  with WordSpecLike with MustMatchers with BeforeAndAfterAll {

  override def afterAll: Unit = {
    shutdown(system)
  }

  "a device" when {
    "monitoring temperature" must {
      "reply with empty reading if no temperature is known" in {
        val probe = TestProbe()
        val deviceActor: ActorRef = system.actorOf(Device.props("group", "device"))

        deviceActor.tell(Device.ReadTemperature(requestId = 42), probe.ref)
        val response: Device.RespondTemperature = probe.expectMsgType[Device.RespondTemperature]
        response.requestId mustBe 42
        response.value mustBe None
      }
      "reply with a confirmation once a reading is stored" in {
        val probe = TestProbe()
        val deviceActor: ActorRef = system.actorOf(Device.props("group", "device"))

        deviceActor.tell(Device.RecordTemperature(requestId = 42, value = 14.21), probe.ref)
        val response: Device.TemperatureRecorded = probe.expectMsgType[Device.TemperatureRecorded]
        response.requestId mustBe 42
      }
      "reply with the most up to date temeprature when it is known" in {
        val probe = TestProbe()
        val deviceActor: ActorRef = system.actorOf(Device.props("group", "device"))

        deviceActor.tell(Device.RecordTemperature(requestId = 42, value = 14.21), probe.ref)
        probe.expectMsgType[Device.TemperatureRecorded]

        deviceActor.tell(Device.ReadTemperature(requestId = 43), probe.ref)
        val response: Device.RespondTemperature = probe.expectMsgType[Device.RespondTemperature]
        response.requestId mustBe 43
        response.value mustBe Some(14.21)
      }
    }
    "being registered" must {
      "reply with a registration response after successful registration" in {

        val group = "group1"
        val device = "device1"

        val probe = TestProbe()
        val deviceActor: ActorRef = system.actorOf(Device.props(group, device))

        deviceActor.tell(DeviceManager.RequestTrackDevice(group, device), probe.ref)
        probe.expectMsg(DeviceManager.DeviceRegistered)
      }
      "not register if the group does not match" in {

        val group = "group1"
        val device = "device1"

        val probe = TestProbe()
        val deviceActor: ActorRef = system.actorOf(Device.props(group, device))

        deviceActor.tell(DeviceManager.RequestTrackDevice("group2", device), probe.ref)
        probe.expectNoMessage()
      }
      "not register if the device does not match" in {

        val group = "group1"
        val device = "device1"

        val probe = TestProbe()
        val deviceActor: ActorRef = system.actorOf(Device.props(group, device))

        deviceActor.tell(DeviceManager.RequestTrackDevice(group, "device2"), probe.ref)
        probe.expectNoMessage()
      }
    }
  }
}
