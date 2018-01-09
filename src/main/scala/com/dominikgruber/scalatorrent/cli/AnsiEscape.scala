package com.dominikgruber.scalatorrent.cli

/**
  * ANSI escape sequences.
  * For moving the cursor around in the terminal.
  *
  * https://stackoverflow.com/a/24629164/2004857
  * http://www.termsys.demon.co.uk/vtansi.htm
  * https://en.wikipedia.org/wiki/ANSI_escape_code#Non-CSI_codes
  */
object AnsiEscape {

  def esc(s: String) = "\u001b[" + s
  val Save = esc("s")
  val Restore = esc("u")
  def up(n: Int = 1) = esc(n + "A")
  def down(n: Int = 1) = esc(n + "B")
  def right(n: Int = 1) = esc(n + "C")
  def left(n: Int = 1) = esc(n + "D")

  val ClearRight = esc("0K")
  val ClearLeft = esc("1K")
  val ClearLine = esc("2K")

}
