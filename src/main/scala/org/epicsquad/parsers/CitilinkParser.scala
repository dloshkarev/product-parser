package org.epicsquad.parsers

import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.elementList
import org.epicsquad.Product

//TODO: doesn't work. Maybe later...
class CitilinkParser extends ProductParser {
  override protected val baseUrl: String = "https://www.citilink.ru"
  override protected val source: String = "citilink"

  override def parseProductUrls(productUrlsFile: String): Seq[String] = {
    val stop = Set(
      "https://www.citilink.ru/about/corporate/softsubscription/",
      "https://www.citilink.ru/about/delivery/",
      "https://www.citilink.ru/supplies/",
      "https://www.citilink.ru/about/service/zaschita/",
      "https://www.citilink.ru/about/service/strahovanie/",
      "https://www.citilink.ru/services/",
      "https://www.citilink.ru/about/service/setup/",
      "https://www.citilink.ru/about/service/viezd/",
      "https://www.citilink.ru/about/service/configuration/"
    )

    val menuUrls = prepareUrls(getProxyDoc(baseUrl) >> elementList(".subcategory-list-item__link-title a")).filterNot(stop.contains)
    val productUrls = menuUrls.zipWithIndex.flatMap { case (menuUrl, i) =>
      try {
        val productUrls = getProductUrls(menuUrl)
        appendLinesToFile(productUrlsFile, productUrls)
        logger.info(s"Done $i from ${menuUrls.size}")
        productUrls
      } catch {
        case e: Throwable =>
          logger.error(s"Something went wrong!", e)
          None
      }
    }.distinct
    productUrls
  }

  def getProductUrls(categoryUrl: String, page: Int = 1, acc: List[String] = List()): List[String] = {
    val url = categoryUrl + "?p=" + page
    try {
      val doc = getProxyDoc(toUrl(url))
      val title = doc >> text("title")
      logger.info(title)
      val productUrls = (doc >> elementList(".product_category_list a.ddl_product_link")).flatMap(_.href)
      if ((doc >?> element("li.next")).isEmpty) acc ++ productUrls
      else getProductUrls(categoryUrl, page + 1, acc ++ productUrls)
    } catch {
      case _: Throwable =>
        logger.info(s"Cannot parse page by $url")
        acc
    }
  }

  override protected def parseProduct(doc: JsoupBrowser.JsoupDocument, url: String): Product = {
    val category = (doc >?> elementList(".breadcrumbs a")).flatMap(x => Option(x.drop(1).map(_.text).mkString("/")))
    val name = doc >> text("h1")
    val brand = doc >?> element("div#flix-minisite") match {
      case Some(e) => Some(e.attr("data-flix-brand"))
      case None => None
    }
    Product(source, url, name, brand, category)
  }
}
