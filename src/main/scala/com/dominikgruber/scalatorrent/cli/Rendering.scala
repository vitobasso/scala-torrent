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

  /**
    * Keep track of how many rows we currently occupy in the terminal, to decide when to add more.
    */
  private var rowsTaken: Int = 0

  def newLayout(nSections: Int): Layout = Layout(cols, nSections)

  def render(layout: Layout): Unit = {
    val totalHeight = layout.height
    stretch(totalHeight)
    clearLines(
      top = totalHeight + 1, //let the prompt be in the bottom row
      height = totalHeight)
    layout.sections.foreach(renderSection)
  }

  private def renderSection(section: Section): Unit = {
    val begin = "\r" + up() * section.top
    print(s"$Save$begin${section.string}$Restore")
  }

  private def clearLines(top: Int, height: Int): Unit = {
    val begin = up() * top
    val clearOne = "\r" + ClearLine + down()
    val clearAll = begin + clearOne * height
    print(s"$Save$clearAll$Restore")
  }

  private def stretch(height: Int): Unit = {
    val newRows = height - rowsTaken
    print("\n" * newRows)
    rowsTaken += newRows
  }


}
