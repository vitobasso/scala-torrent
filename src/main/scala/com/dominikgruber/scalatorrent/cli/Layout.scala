package com.dominikgruber.scalatorrent.cli

import com.dominikgruber.scalatorrent.cli.Layout.Section

/**
  * A vertical stack of sections to be rendered in the terminal.
  * Indexes are zero based and count from the bottom up.
  */
case class Layout(cols: Int, sections: List[Section] = Nil) {
  require(cols > 0)

  def size: Int = sections.size
  def height: Int = sections.lastOption.map(_.top).getOrElse(0)

  def addBottom(string: String): Layout = { //TODO updated(0, string)
    val newSection = section(string)
    val oldSectionsUpdated = sections.map(_.movedUp(newSection.height))
    val allSections = newSection :: oldSectionsUpdated
    Layout(cols, allSections)
  }

  def updated(i: Int, string: String): Layout = {
    require(i < size)
    val sectionsBelow = sections.take(i)
    val bottom = sectionsBelow.lastOption.map(_.top).getOrElse(0)
    val newSection = section(string).movedUp(bottom)
    val oldSection = sections(i)
    val sectionsAbove = sections.drop(i+1)
      .map(_.movedDown(oldSection.height))
      .map(_.movedUp(newSection.height))
    val allSections = sectionsBelow ++ (newSection :: sectionsAbove)
    Layout(cols, allSections)
  }

  private def section(string: String): Section = {
    val height = string.split('\n').map(wrappedLineHeight).sum
    Section(string, height, height)
  }

  private def wrappedLineHeight(line: String): Int =
    line.length / cols + 1

}

object Layout {

  def apply(cols: Int, nSections: Int): Layout = {
    require(nSections >= 0)
    if(nSections == 0) Layout(cols)
    else apply(cols, nSections - 1).addBottom("")
  }

  case class Section(string: String, top: Int, height: Int) {
    def movedUp(rows: Int): Section = Section(string, top + rows, height)
    def movedDown(rows: Int): Section = movedUp(-rows)
  }

}