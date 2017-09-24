package com.dominikgruber.scalatorrent.util

import akka.actor.{ActorRef, ActorSystem, Terminated}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._


abstract class ActorSpec extends TestKit(ActorSystem())
  with ImplicitSender with WordSpecLike with Matchers
  with BeforeAndAfterAll with MockitoSugar {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  def syncStop(actor: ActorRef) {
    watch(actor)
    system.stop(actor)
    expectMsgPF(10 seconds){
      case Terminated(`actor`) =>
    }
    unwatch(actor)
  }

}
