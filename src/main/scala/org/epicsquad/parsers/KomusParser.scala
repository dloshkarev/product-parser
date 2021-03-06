package org.epicsquad.parsers

import java.nio.file.{Files, Paths}

import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.elementList
import org.epicsquad.Product

import scala.collection.JavaConverters.seqAsJavaListConverter

class KomusParser extends ProductParser {
  override protected val baseUrl: String = "https://www.komus.ru"
  override protected val source: String = "komus"

  val menuCss = "a.b-account__item--label"

  val stop = Set(
    "https://www.komus.ru/news/statichnye-stranitsy/vkusnosti/n/210114/?from=menu",
    "https://www.komus.ru/news/statichnye-stranitsy/komus-diskont/n/7321143/?from=menu"
  )

  override def parseProductUrls(productUrlsFile: String): Seq[String] = {
    val topMenuUrls = prepareUrls(browser.get(baseUrl) >> elementList("a.js-menuItemLink")).map { u =>
      if (u.startsWith("http")) u
      else toUrl(u)
    }.filterNot(stop.contains)
    val productUrls = topMenuUrls.flatMap { topMenuUrl =>
      processMenu(topMenuUrl)
    }.distinct
    Files.write(Paths.get(productUrlsFile), productUrls.asJava)
    productUrls
  }

  def processMenu(topMenuUrl: String): Seq[String] = {
    val doc = browser.get(toUrl(topMenuUrl))
    val imageMenu = prepareUrls(doc >> elementList("a.b-menu__item.b-collection__item"))
    val innerMenu = prepareUrls(doc >> elementList(menuCss))
    if (imageMenu.isEmpty && innerMenu.isEmpty) {
      val productUrls = getProductUrls(topMenuUrl)
      logger.info(s"Found: ${productUrls.size}")
      productUrls
    }
    else {
      val menuUrls = if (innerMenu.nonEmpty) innerMenu else imageMenu
      menuUrls.flatMap(processMenu)
    }
  }

  def getProductUrls(categoryUrl: String, page: Int = 1, acc: List[String] = List()): List[String] = {
    val url = categoryUrl + "?page=" + page
    try {
      val doc = browser.get(toUrl(url))
      val title = doc >> text("title")
      logger.info(title)
      val needToStop = (doc >?> element(".b-pageNumber__item--next")).isEmpty
      val productUrls = (doc >> elementList("a.b-productList__item__descr--title")).flatMap(_.href).map(u => baseUrl + u)
      if (needToStop) acc ++ productUrls
      else getProductUrls(categoryUrl, page + 1, acc ++ productUrls)
    } catch {
      case _: Throwable =>
        logger.info(s"Cannot parse page by $url")
        acc
    }
  }

  override protected def parseProduct(doc: JsoupBrowser.JsoupDocument, url: String): Product = {
    val category = (doc >?> elementList(".b-breadcrumbs__list a")).flatMap(x => Option(x.drop(2).map(_.text).mkString("/")))
    val name = doc >> text("h1")
    val brand = doc >?> text(".i-dib.i-pl2 a")
    Product(source, url, name, brand, category)
  }
}
