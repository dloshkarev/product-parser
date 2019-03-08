package org.epicsquad.parsers

import java.nio.file.{Files, Paths}

import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import org.epicsquad.Product

import scala.collection.JavaConversions._
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.mutable

class AuchanParser extends ProductParser {
  override val baseUrl: String = "https://www.auchan.ru/"

  override def parseProductUrls(productUrlsFile: String): Seq[String] = {
    deleteFileIfExists(productUrlsFile)
    val menuUrls = prepareUrls(browser.get(baseUrl) >> elementList(".m-menu__submenu-items--other a, .m-menu__submenu-heading a"))
    val productUrls = menuUrls.zipWithIndex.flatMap { case (menuUrl, i) =>
      try {
        logger.info(s"Done $i from ${menuUrls.size}")
        getProductUrls(menuUrl)
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

  override protected def parseProductsChunk(urlsChunk: Seq[String], productsFile: String): Unit = {
    logger.info("Parsing products started")
    val buffer = mutable.ListBuffer.empty[String]
    urlsChunk.zipWithIndex.foreach { case (url, i) =>
      if (i % 50 == 0) {
        appendLinesToFile(productsFile, buffer)
        buffer.clear()
        logger.info(s"$i of ${urlsChunk.size} stored into $productsFile")
      }
      try {
        val doc = browser.get(url)
        val name = doc >> text("h1")
        val category = (doc >> elementList(".breadcrumbs__list a")).drop(1).map(_.text).mkString("/")
        val brand = parseMeta(doc, "brand")
        val product = Product(url, name, brand, Some(category))
        buffer += product.toCsv + "\n"
      } catch {
        case e: Throwable =>
          logger.error("Error during product parsing: " + url)
          val product = Product(url)
          buffer += product.toCsv + ";" + e.getLocalizedMessage + "\n"
      }
    }
    logger.info("Parsing products finished")
    if (buffer.nonEmpty) {
      appendLinesToFile(productsFile, buffer)
    }
  }
}
