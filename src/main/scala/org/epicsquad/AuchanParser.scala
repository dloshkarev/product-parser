package org.epicsquad

import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.StrictLogging
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._

import scala.collection.JavaConversions._
import scala.collection.JavaConverters.seqAsJavaListConverter

class AuchanParser extends ProductParser with StrictLogging {
  override val baseUrl: String = "https://www.auchan.ru/"

  override def parse(partsDirectory: String, productUrlsFile: String): Seq[Product] = {
    logger.info("Parsing started")
    val productUrls = parseProductUrls(partsDirectory, productUrlsFile)
    webDriver.close()
    logger.info(s"Parsing finished. Wrote ${productUrls.size} into $productUrlsFile")
    Seq()
  }

  def parseProductUrls(partsDirectory: String, productUrlsFile: String): Seq[String] = {
    val menuUrls = prepareUrls(browser.get(baseUrl) >> elementList(".m-menu__submenu-items--other a, .m-menu__submenu-heading a"))
    val productUrls = menuUrls.zipWithIndex.flatMap { case (menuUrl, i) =>
      try {
        val menuProducts = getProductUrls(menuUrl)
        val partFileName = partsDirectory + i + ".txt"
        logger.info(s"Done $i from ${menuUrls.size}. Wrote ${menuProducts.size} into $partFileName")
        Files.write(Paths.get(partFileName), menuProducts.asJava)
        menuProducts
      } catch {
        case e: Throwable =>
          logger.error(s"Something went wrong!", e)
          None
      }
    }.distinct
    Files.write(Paths.get(productUrlsFile), productUrls.asJava)
    productUrls
  }

  def getProductUrls(categoryUrl: String, page: Int = 1, acc: List[String] = List()): List[String] = {
    val url = categoryUrl + "?p=" + page
    webDriver.get(url)
    val title = webDriver.getTitle
    logger.info(title)
    if ((page > 1 && !webDriver.getCurrentUrl.contains("?p=")) || title == "Страница не найдена - Интернет магазин Ашан") acc
    else {
      val productUrls = webDriver.findElementsByCssSelector(".products__item-link").toList.flatMap(_.href)
      getProductUrls(categoryUrl, page + 1, acc ++ productUrls)
    }
  }
}
