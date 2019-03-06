package org.epicsquad

object Main extends App {
  val parser = new AuchanParser
  parser.parse("/home/dloshkarev/Downloads/auchan-products.txt")
}
