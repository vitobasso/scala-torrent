package com.dominikgruber.scalatorrent.cli

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.dominikgruber.scalatorrent.Boot.quit
import com.dominikgruber.scalatorrent.Coordinator.{AddTorrentFile, TorrentAddedSuccessfully, TorrentFileInvalid}

import scala.concurrent.duration._
import scala.io.StdIn

case class UserInteraction(frontend: ActorRef, coordinator: ActorRef) {

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
      implicit val timeout: Timeout = Timeout(5.seconds)
      import scala.concurrent.ExecutionContext.Implicits.global
      (coordinator ? AddTorrentFile(file)) onSuccess {
        case TorrentAddedSuccessfully(file1, torrent) =>
          print(s"Added $file1.\n> ")
        case TorrentFileInvalid(file1, message) =>
          print(s"Failed to add $file1: $message\n> ")
        case _ =>
      }
    }
  }

  def printHelp(): Unit = {
    println("add <path>     Add a torrent file")
    println("quit           Quit the client")
  }

}
