package com.dominikgruber.scalatorrent.util

import akka.actor.{ActorRef, ActorSystem, Terminated}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.language.postfixOps


abstract class ActorIT extends TestKit(ActorSystem())
  with ImplicitSender with WordSpecLike with Matchers
  with BeforeAndAfterAll with MockFactory {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  def syncStop(actor: ActorRef): Unit = {
    watch(actor)
    system.stop(actor)
    fishForMessage(10 seconds){
      case Terminated(`actor`) => true
      case _ => false
    }
    unwatch(actor)
  }

}
