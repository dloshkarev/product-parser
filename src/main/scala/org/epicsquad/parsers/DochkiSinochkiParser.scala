package org.epicsquad.parsers

import java.nio.file.{Files, Paths}

import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.elementList
import org.epicsquad.Product

import scala.collection.JavaConverters.seqAsJavaListConverter

class DochkiSinochkiParser extends ProductParser {
  override protected val baseUrl: String = "https://www.dochkisinochki.ru"

  override def parseProductUrls(productUrlsFile: String): Seq[String] = {
    val stop = Set(
      "https://www.dochkisinochki.ru/icatalog/categories/lego/polrebenka-dlya_devochek/",
      "https://www.dochkisinochki.ru/icatalog/categories/rasprodaja_dlya_malishei/",
      "https://www.dochkisinochki.ru/icatalog/categories/rasprodazha_3_2/",
      "https://www.dochkisinochki.ru/icatalog/categories/outlet/",
      "https://www.dochkisinochki.ru/icatalog/categories/kerry_vesna_2019/",
      "https://www.dochkisinochki.ru/icatalog/categories/podarki_8_marta/",
      "https://www.dochkisinochki.ru/icatalog/categories/ch2491723/",
      "https://www.dochkisinochki.ru/icatalog/categories/igrushki-skidki-do-80/",
      "https://www.dochkisinochki.ru/icatalog/categories/globalnaya-rasprodaja-rukzakov/",
      "https://www.dochkisinochki.ru/icatalog/categories/sladkiy-son-postelnoe-bele-i-pizhamy-skidki-do-50/",
      "https://www.dochkisinochki.ru/icatalog/categories/verkhnyaya-odezhda-i-golovnye-ubory-huppa-vesna-leto-2019/",
      "https://www.dochkisinochki.ru/icatalog/categories/novaya-kolletsiya-odezhdy-dlya-malyshey/",
      "https://www.dochkisinochki.ru/icatalog/categories/3363588/",
      "https://www.dochkisinochki.ru/icatalog/categories/verkhnyaya-odezhda-i-golovnye-ubory-lassie-by-reima-vesna-leto-2019/",
      "https://www.dochkisinochki.ru/icatalog/categories/new-collection-ob-2019/",
      "https://www.dochkisinochki.ru/icatalog/categories/vesna-leto-2019-boom-by-orby/",
      "https://www.dochkisinochki.ru/icatalog/categories/novinki-verkhney-odezhdy-i-golovnykh-uborov-artel-i-batik-vesna-leto-2019/",
      "https://www.dochkisinochki.ru/icatalog/categories/novinki-odezhdy-vesna-osen-2019/",
      "https://www.dochkisinochki.ru/icatalog/categories/new-collection-ob-2019/",
      "https://www.dochkisinochki.ru/icatalog/categories/3551987/",
      "https://www.dochkisinochki.ru/icatalog/categories/rasprodazha_monopoly/",
      "https://www.dochkisinochki.ru/icatalog/categories/fenix-skidka-50/",
      "https://www.dochkisinochki.ru/icatalog/categories/lost_size/",
      "https://www.dochkisinochki.ru/icatalog/categories/super-zim-ob/"
    )
    val menuUrls = prepareUrls(browser.get(baseUrl) >> elementList(".shop-main-menu_submenu-item--title"))
      .map(u => baseUrl + u)
      .filterNot(stop.contains)
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

  def getProductUrls(categoryUrl: String, page: Int = 1, acc: List[String] = List(), needToStop: Boolean = false): List[String] = {
    val url = categoryUrl + "?line=&PAGEN_1=" + page
    try {
      val doc = browser.get(url)
      val title = doc >> text("title")
      logger.info(title)
      if (needToStop) acc
      else {
        val productUrls = (doc >> elementList(".news_bottom a")).flatMap(_.href).map(u => baseUrl + u)
        val lastPage = doc >?> text(".pagination-new a:last-of-type")
        getProductUrls(categoryUrl, page + 1, acc ++ productUrls, needToStop = lastPage.isEmpty || lastPage.get != "next")
      }
    } catch {
      case e: Throwable =>
        logger.info(s"Cannot parse page by $url", e)
        acc
    }
  }

  override protected def parseProduct(doc: JsoupDocument, url: String): Product = {
    val category = (doc >?> elementList("li[itemtype=\"https://data-vocabulary.org/Breadcrumb\"] > a > span")).flatMap(x => Option(x.map(_.text).mkString("/")))
    val name = doc >> text("h1")
    val brand = doc >?> text(".info span a")
    Product(url, name, brand, category)
  }
}
