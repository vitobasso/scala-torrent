package com.dominikgruber.scalatorrent.cli

import com.dominikgruber.scalatorrent.cli.AnsiEscape._
import com.dominikgruber.scalatorrent.cli.Layout.Section
import org.jline.terminal.{Terminal, TerminalBuilder}

/**
  * Row coordinates count from the bottom up.
  */
class Rendering {

  private val terminal: Terminal = TerminalBuilder.terminal
  private val cols: Int = terminal.getSize.getColumns
  private val rows: Int = terminal.getSize.getRows

  def newLayout: Layout = Layout(cols)

  def render(layout: Layout): Unit = {
    clearLines(top = rows, height = rows - 1) //let the prompt be in the bottom row
    layout.sections.foreach(renderSection)
  }

  private def renderSection(section: Section): Unit = {
    val begin = '\r' + up() * section.top
    print(s"$Save$begin${section.string}$Restore")
  }

  private def clearLines(top: Int, height: Int): Unit = {
    val begin = up() * top
    val clearOne = '\r' + ClearLine + down()
    val clearAll = begin + clearOne * height
    print(s"$Save$clearAll$Restore")
  }

}
