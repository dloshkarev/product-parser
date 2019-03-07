package org.epicsquad

import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.StrictLogging
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._

import scala.collection.JavaConversions._
import scala.collection.JavaConverters.seqAsJavaListConverter

class AuchanParser extends ProductParser with StrictLogging {
  override val baseUrl: String = "https://www.auchan.ru/"

  override def parse(productUrlsFile: String): Seq[Product] = {
    logger.info("Parsing started")
    val productUrls = parseProductUrls(productUrlsFile)
    webDriver.close()
    Seq()
  }

  def parseProductUrls(productUrlsFile: String): Set[String] = {
    val menuUrls = prepareUrls(browser.get(baseUrl) >> elementList(".m-menu__items a"))
    val productUrls = menuUrls.flatMap { menuUrl =>
      try {
        val standardCategories = prepareUrls(browser.get(menuUrl) >> elementList(".category__item-title a"))
        if (standardCategories.nonEmpty) {
          standardCategories.flatMap { categoryUrl =>
            getProductUrls(categoryUrl)
          }
        } else {
          val imagesCategories = prepareUrls(browser.get(menuUrl) >> elementList(".ldo_cat_block a"))
          if (imagesCategories.nonEmpty) {
            imagesCategories.flatMap { categoryUrl =>
              getProductUrls(categoryUrl)
            }
          } else {
            getProductUrls(menuUrl)
          }
        }
      } catch {
        case e: Throwable =>
          logger.error(s"Something went wrong!", e)
          None
      }
    }
    Files.write(Paths.get(productUrlsFile), productUrls.asJava)
    productUrls.toSet
  }

  def getProductUrls(categoryUrl: String, page: Int = 1, acc: List[String] = List()): List[String] = {
    val url = categoryUrl + "?p=" + page
    webDriver.get(url)
    val title = webDriver.getTitle
    logger.info(title)
    if (title == "Страница не найдена - Интернет магазин Ашан") acc
    else {
      val productUrls = webDriver.findElementsByCssSelector(".products__item-link").toList.flatMap(_.href)
      getProductUrls(categoryUrl, page + 1, acc ++ productUrls)
    }
  }
}
