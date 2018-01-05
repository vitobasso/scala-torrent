package com.dominikgruber.scalatorrent

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.dominikgruber.scalatorrent.Coordinator._
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.postfixOps

object Boot extends App {

  println("")
  println("                    __            __                             __")
  println("   ______________ _/ /___ _      / /_____  _____________  ____  / /_")
  println("  / ___/ ___/ __ `/ / __ `/_____/ __/ __ \\/ ___/ ___/ _ \\/ __ \\/ __/")
  println(" (__  ) /__/ /_/ / / /_/ /_____/ /_/ /_/ / /  / /  /  __/ / / / /_")
  println("/____/\\___/\\__,_/_/\\__,_/      \\__/\\____/_/  /_/   \\___/_/ /_/\\__/")
  println("")
  println("")

  val log: Logger = LoggerFactory.getLogger(Boot.getClass)

  sys.addShutdownHook{ quit() }

  // Start actor system and coordinator actor
  implicit val system = ActorSystem("scala-torrent")
  val coordinator: ActorRef = system.actorOf(Props(classOf[Coordinator]), "coordinator")

  // TMP
//  addTorrentFile("/Users/victorbasso/Documents/workspace/scala-torrent/src/test/resources/metainfo/ubuntu-12.04.5-desktop-amd64.iso.torrent")
//  addTorrentFile("/Users/victorbasso/Documents/workspace/scala-torrent/src/test/resources/metainfo/gimp-2.8.22-x86_64.dmg.torrent")
  addTorrentFile("/Users/victorbasso/Documents/workspace/scala-torrent/src/test/resources/metainfo/sintel.torrent")
//  addTorrentFile("/Users/victorbasso/Documents/workspace/scala-torrent/src/test/resources/metainfo/CC_1916_07_10_TheVagabond_archive.torrent")
//  addTorrentFile("/Users/victorbasso/Downloads/no_checksums.torrent")
  // TMP

  // Listen for commands
  print("> ")
  Iterator.continually(StdIn.readLine()).foreach { cmd =>
    cmd match {
      case _ if cmd.startsWith("add ") => addTorrentFile(cmd.substring(4).trim)
      case "help" => printHelp()
      case "quit" => quit()
      case "exit" => quit()
      case _ if !cmd.trim.isEmpty =>
        println("Unknown command. Type 'help' for a list of all commands.")
      case _ =>
    }
    print("> ")
  }

  def addTorrentFile(file: String): Unit = {
    if (file.isEmpty) {
      println("No file specified. See 'help' for further instructions.")
    } else {
      implicit val timeout = Timeout(5 seconds)
      (coordinator ? AddTorrentFile(file)) onSuccess {
        case TorrentAddedSuccessfully(file1, torrent) =>
          print(s"Added $file1.\n> ")
        case TorrentFileInvalid(file1, message) =>
          print(s"Failed to add $file1: $message\n> ")
        case _ =>
      }
    }
  }

  def printHelp() = {
    println("add <path>     Add a torrent file")
    println("quit           Quit the client")
  }

  def quit() = {
    println("Shutting down scala-torrent...")
    // TODO: Notify coordinator and wait for ACK (connections need to be properly closed)
    system.terminate().onComplete {
      _ => System.exit(0)
    }
  }

}