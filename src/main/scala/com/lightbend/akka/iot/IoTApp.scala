package com.lightbend.akka.iot

import akka.actor.{ActorRef, ActorSystem}

import scala.io.StdIn

object IoTApp extends App {
  val system = ActorSystem("iot-system")

  try {
    // Create top level supervisor
    val supervisor: ActorRef = system.actorOf(IotSupervisor.props(), "iot-supervisor")
    // Exit the system after ENTER is pressed
    StdIn.readLine()
  } finally {
    system.terminate()
  }
}
