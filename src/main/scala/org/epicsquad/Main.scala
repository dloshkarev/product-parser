package org.epicsquad

import org.epicsquad.parsers.{CastoramaParser, WildberriesParser}

object Main extends App {
  /*val parser = new CastoramaParser
  parser.parseProductUrls(
    "D:\\castorama-urls.txt"
  )*/
  /*val parser = new WildberriesParser
  parser.parseProductsFromFile(
    "D:\\wildberries-urls.txt",
    "D:\\wildberries-products.txt"
  )*/

  val parser = new CastoramaParser
  parser.parseProductsFromFile(
    "D:\\castorama-urls.txt",
    "D:\\castorama-products.txt"
  )
}
