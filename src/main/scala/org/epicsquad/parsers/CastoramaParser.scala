package org.epicsquad.parsers

import java.nio.file.{Files, Paths}

import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.elementList
import org.epicsquad.Product

import scala.collection.JavaConverters.seqAsJavaListConverter

class CastoramaParser extends ProductParser {
  override protected val baseUrl: String = "https://www.castorama.ru"
  override protected val source: String = "castorama"

  override def parseProductUrls(productUrlsFile: String): Seq[String] = {
    val menuUrls = prepareUrls(browser.get(baseUrl) >> elementList(".sitemap-topmenu-link.level1"))
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
    val url = categoryUrl + "?limit=96&p=" + page
    try {
      val doc = browser.get(url)
      val title = doc >> text("title")
      logger.info(title)
      if (page != 1 && !doc.location.contains("&p=" + page)) acc
      else {
        val productUrls = (doc >> elementList(".main-container .product-name a")).flatMap(_.href)
        getProductUrls(categoryUrl, page + 1, acc ++ productUrls)
      }
    } catch {
      case _: Throwable =>
        logger.info(s"Cannot parse page by $url")
        acc
    }
  }

  override protected def parseProduct(doc: JsoupBrowser.JsoupDocument, url: String): Product = {
    val category = (doc >?> elementList(".breadcrumbs a")).flatMap(x => Option(x.drop(1).map(_.text).mkString("/")))
    val name = doc >> text("h1")
    Product(source, url, name, None, category)
  }
}
