package com.dominikgruber.scalatorrent

import akka.actor.{ActorRef, ActorSystem, Props}
import com.dominikgruber.scalatorrent.cli.{CliActor, UserInteraction}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

object Boot extends App {

  val log: Logger = LoggerFactory.getLogger(Boot.getClass)
  val config = AppConfig.load

  sys.addShutdownHook(quit())

  // Start actor system and coordinator actor
  val system = ActorSystem("scala-torrent")
  val cli: ActorRef = system.actorOf(CliActor.props(config.cli), "cli")
  val coordinator: ActorRef = system.actorOf(Coordinator.props(cli, config.coordinator), "coordinator")
  UserInteraction(cli, coordinator)

  def quit(): Unit = {
    println("Shutting down scala-torrent...")
    // TODO: Notify coordinator and wait for ACK (connections need to be properly closed)
    system.terminate().onComplete {
      _ => System.exit(0)
    }
  }

}