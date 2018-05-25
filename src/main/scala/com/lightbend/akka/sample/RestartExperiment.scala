package com.lightbend.akka.sample

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object SupervisingActor {
  val KILL_CHILD = "failChild"
}

class SupervisingActor extends Actor {
  val child: ActorRef = context.actorOf(Props[SupervisedActor], "supervised-actor")

  override def receive: Receive = {
    case SupervisingActor.KILL_CHILD ⇒ child ! "fail"
  }
}

class SupervisedActor extends Actor {
  override def preStart(): Unit = println("supervised actor started")

  override def postStop(): Unit = println("supervised actor stopped")

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    println(s"Oh no - ${reason.getMessage}")
  }

  override def postRestart(reason: Throwable): Unit = {
    println("I'm okay again now though")
  }

  override def receive: Receive = {
    case "fail" ⇒
      println("supervised actor fails now")
      throw new Exception("I failed!")
  }
}

object RestartExperiment extends App {

  val system = ActorSystem("supervisor")

  val guardian = system.actorOf(Props[SupervisingActor])

  guardian ! SupervisingActor.KILL_CHILD

  Thread.sleep(1000) // wait for the child to be revived to avoid dead-letters

  guardian ! SupervisingActor.KILL_CHILD

  system.terminate()
}
