package com.dominikgruber.scalatorrent.cli

import com.dominikgruber.scalatorrent.cli.AnsiEscape._
import com.dominikgruber.scalatorrent.cli.Layout.Section

/**
  * Row coordinates count from the bottom up.
  */
object Rendering {

  /**
    * Keep track of how many lines we currently occupy in the terminal, to decide when to add more.
    */
  private var linesTaken: Int = 0

  def newLayout(nSections: Int): Layout =
    Layout(TerminalSetup.cols, nSections)

  def render(layout: Layout): Unit = {
    val height = layout.height
    stretch(height)
    clearLines(top = height, height = height)
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
    val newRows = height - linesTaken
    print("\n" * newRows)
    linesTaken += newRows
  }


}
