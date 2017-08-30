package com.dominikgruber.scalatorrent.util

import akka.actor.{ActorRef, ActorSystem, Terminated}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}


abstract class ActorSpec extends TestKit(ActorSystem())
  with ImplicitSender with WordSpecLike with Matchers
  with BeforeAndAfterAll with MockitoSugar {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  def syncStop(actor: ActorRef) {
    watch(actor)
    system.stop(actor)
    expectMsgPF(){
      case Terminated(`actor`) =>
    }
    unwatch(actor)
  }

}
