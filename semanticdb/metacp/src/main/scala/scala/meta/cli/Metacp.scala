package scala.meta.cli

import scala.meta.internal.metacp._

object Metacp {
  def main(args: Array[String]): Unit = {
    sys.exit(process(args))
  }

  def process(args: Array[String]): Int = {
    Main.process(args)
  }
}
