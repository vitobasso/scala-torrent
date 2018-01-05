package com.dominikgruber.scalatorrent.cli

import com.dominikgruber.scalatorrent.cli.Layout.Section

/**
  * A vertical stack of sections to be rendered in the terminal.
  */
case class Layout(cols: Int, sections: List[Section] = Nil) {

  def size: Int = sections.size

  def addBottom(string: String): Layout = {
    val newSection = section(string)
    val oldSectionsUpdated = sections.map(_.movedUp(newSection.height))
    val allSections = oldSectionsUpdated :+ newSection
    Layout(cols, allSections)
  }

  def replace(i: Int, string: String): Layout = {
    require(i < size)
    val newSection = section(string)
    val newSections = sections.updated(i, newSection)
    Layout(cols, newSections)
  }

  private def section(string: String): Section = {
    val height: Int = string.length / cols + 1
    Section(string, height, height)
  }

}

object Layout {

  case class Section(string: String, top: Int, height: Int) {
    def movedUp(rows: Int): Section = Section(string, top + rows, height)
  }

}