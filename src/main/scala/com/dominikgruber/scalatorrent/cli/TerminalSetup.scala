package com.dominikgruber.scalatorrent.cli

import org.jline.reader.{LineReader, LineReaderBuilder}
import org.jline.terminal.{Terminal, TerminalBuilder}

object TerminalSetup {

  private val terminal: Terminal = {
    val terminal = TerminalBuilder.terminal
    terminal.enterRawMode()
    terminal
  }

  val reader: LineReader = {
    LineReaderBuilder.builder().terminal(terminal).build()
  }

  val cols: Int = terminal.getSize.getColumns

}
