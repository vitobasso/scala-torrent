package com.dominikgruber.scalatorrent.util

import akka.pattern.AskSupport
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

trait Asking extends AskSupport {
  implicit val timeout: Timeout = Timeout(5 seconds)
  implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global
}
