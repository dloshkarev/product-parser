package org.epicsquad

import java.nio.file.{Files, Paths}

import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.JavaConversions._

class AuchanParser extends ProductParser {
  override val baseUrl: String = "https://www.auchan.ru/"

  override def parse(productUrlsFile: String): Seq[Product] = {
    val productUrls = parseProductUrls(productUrlsFile)
    webDriver.close()
    Seq()
  }

  def parseProductUrls(productUrlsFile: String): Set[String] = {
    val productUrls = (browser.get(baseUrl) >> elementList(".m-menu__items a")).flatMap { menu =>
      menu.href match {
        case Some(menuUrl) =>
          (browser.get(menuUrl) >> elementList(".category__item-title a")).flatMap { category =>
            category.href match {
              case Some(categoryUrl) =>
                getProductUrls(categoryUrl)
              case None => throw new ParseException("Category block not found")
            }
          }
        case None => throw new ParseException("Menu block not found")
      }
    }
    Files.write(Paths.get(productUrlsFile), productUrls.asJava)
    productUrls.toSet
  }

  def getProductUrls(categoryUrl: String, page: Int = 1, acc: List[String] = List()): List[String] = {
    val url = categoryUrl + "?p=" + page
    webDriver.get(url)
    val title = webDriver.getTitle
    println(title)
    if (title == "Страница не найдена - Интернет магазин Ашан") acc
    else {
      val productUrls = webDriver.findElementsByCssSelector(".products__item-link").toList.flatMap(_.href)
      Thread.sleep(5000)
      getProductUrls(categoryUrl, page + 1, acc ++ productUrls)
    }
  }
}
