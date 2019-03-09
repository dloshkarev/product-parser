package org.epicsquad

import org.epicsquad.parsers.{AuchanParser, CastoramaParser, KomusParser, WildberriesParser}

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

  /*val parser = new AuchanParser
  parser.parseProductsFromFile(
    "D:\\auchan-urls.txt",
    "D:\\auchan-products.txt",
    50000,
    10000
  )*/

  val parser = new KomusParser
  parser.parseProductsFromFile(
    "D:\\komus-urls.txt",
    "D:\\komus-products.txt"
  )
}
