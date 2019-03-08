package org.epicsquad.parsers

import java.nio.file.{Files, Paths}

import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.elementList
import org.epicsquad.Product

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.mutable

class WildberriesParser extends ProductParser {
  override protected val baseUrl: String = "https://www.wildberries.ru"

  override def parseProductUrls(productUrlsFile: String): Seq[String] = {
    val stop = Set(
      "https://www.wildberries.ru/catalog/podarki",
      "https://www.wildberries.ru/catalog/trends",
      "https://www.wildberries.ru/catalog/0/brand.aspx",
      "https://www.wildberries.ru/catalog/vesna-leto",
      "https://www.wildberries.ru/promotions"
    )
    val topMenuUrls = prepareUrls(browser.get(baseUrl) >> elementList(".topmenus a")).filterNot(stop.contains)
    val productUrls = topMenuUrls.flatMap { topMenuUrl =>
      val menuUrls = prepareUrls(browser.get(topMenuUrl) >> elementList(".j-menu-level2-item a"))
      menuUrls.zipWithIndex.flatMap { case (menuUrl, i) =>
        try {
          logger.info(s"Done $i from ${menuUrls.size}")
          getProductUrls(baseUrl + menuUrl)
        } catch {
          case e: Throwable =>
            logger.error(s"Something went wrong!", e)
            None
        }
      }
    }.distinct
    Files.write(Paths.get(productUrlsFile), productUrls.asJava)
    productUrls
  }

  def getProductUrls(categoryUrl: String, page: Int = 1, acc: List[String] = List()): List[String] = {
    val url = categoryUrl + "?pagesize=200&page=" + page
    try {
      val doc = browser.get(url)
      val title = doc >> text("title")
      logger.info(title)
      if ((doc >?> text("#divGoodsNotFound")).nonEmpty) acc
      else {
        val productUrls = (doc >> elementList(".ref_goods_n_p")).flatMap(_.href)
        getProductUrls(categoryUrl, page + 1, acc ++ productUrls)
      }
    } catch {
      case _: Throwable =>
        logger.info(s"Cannot get page by $url")
        acc
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
        val category = (doc >?> elementList(".breadcrumbs span:not(.divider)")).flatMap(x => Option(x.drop(1).map(_.text).mkString("/")))
        val name = doc >> text("h1")
        val brand = parseMeta(doc, "brand")
        val product = Product(url, name, brand, category)
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
