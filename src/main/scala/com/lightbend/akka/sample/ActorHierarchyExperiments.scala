package com.lightbend.akka.sample

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import scala.io.StdIn

class PrintMyActorRefActor extends Actor {
  override def receive: Receive = {
    case "printit" ⇒
      val secondRef: ActorRef = context.actorOf(Props.empty, "second-actor")
      println(s"Second: $secondRef")
  }
}

object ActorHierarchyExperiments extends App {
  val system = ActorSystem("testSystem")

  val firstRef = system.actorOf(Props[PrintMyActorRefActor], "first-actor")
  println(s"First: $firstRef")
  firstRef ! "printit"

  println(">>> Press ENTER to exit <<<")
  try StdIn.readLine()
  finally system.terminate()
}
