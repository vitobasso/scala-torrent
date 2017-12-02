package com.dominikgruber.scalatorrent.util

import akka.actor.{ActorRef, ActorSystem, Props, Terminated}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
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

  def syncStart(props: Props, name: String): ActorRef = {
    val ref = system.actorOf(props, name)
    val msg = s"$name - started"
    EventFilter.debug(start = msg, occurrences = 1).awaitDone(100.millis)
    ref
  }

}
