package org.epicsquad.parsers

import java.io.FileWriter
import java.net.URL
import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.StrictLogging
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.Element
import org.epicsquad.{Product, Settings}
import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.mutable
import scala.io.Source
import scala.reflect.io.File

trait ProductParser extends StrictLogging {
  protected val browser = new JsoupBrowser
  private val capabilities = DesiredCapabilities.chrome()
  private val webDriverOpt: Option[RemoteWebDriver] = try {
    Some(new RemoteWebDriver(new URL(Settings.seleniumUrl), capabilities))
  } catch {
    case e: Throwable =>
      logger.error("Cannot find selenium standalone server")
      None
  }

  protected val baseUrl: String

  implicit def elementToExt(e: Element): ElementExt = new ElementExt(e)

  implicit def webElementToExt(e: WebElement): WebElementExt = new WebElementExt(e)

  protected def prepareUrls(elems: Seq[Element]) = elems.flatMap(_.href).filterNot(_ == "#").distinct

  protected def deleteFileIfExists(fileName: String) = {
    val file = File(fileName)
    if (file.exists) file.delete()
  }

  protected def appendLinesToFile(file: String, lines: Seq[String]) = {
    val fw = new FileWriter(file, true)
    try {
      lines.foreach(line => fw.write(line))
    } finally fw.close()
  }

  protected def webDriver: RemoteWebDriver = {
    if (webDriverOpt.nonEmpty) webDriverOpt.get
    else throw new RuntimeException("Selenium standalone server not started!")
  }

  def parse(productUrlsFile: String, productsFile: String): Unit = {
    logger.info("Parsing urls started")
    val productUrls = parseProductUrls(productUrlsFile)
    logger.info(s"Parsing urls finished. Stored ${productUrls.size} into $productUrlsFile")
    logger.info("Parsing products started")
    parseProducts(productUrls, productsFile)
    logger.info(s"Parsing products finished. Stored into $productsFile")
    if (webDriverOpt.nonEmpty) {
      webDriver.close()
    }
  }

  def exportProducts(productFile: String, outFile: String): Unit = {
    val products = Source.fromFile(productFile).getLines().toSeq.map(Product.fromCsv)
    val (correct, incorrect) = products.partition(_.name.nonEmpty)
    logger.info(s"Found ${incorrect.size} incorrect lines. Try to fix...")
    val (corrected, stillIncorrect) = restoreProductsRecursively(incorrect)

    val fullCorrect = if (stillIncorrect.isEmpty) correct ++ corrected
    else correct ++ corrected ++ stillIncorrect
    Files.write(Paths.get(productFile), fullCorrect.map(_.toCsv).asJava)

    if (stillIncorrect.isEmpty) {
      logger.info(s"${corrected.size} corrected products added into: $productFile")
      Files.write(Paths.get(outFile), fullCorrect.map(_.toExport).asJava)
      logger.info(s"${fullCorrect.size} products exported into: $outFile")
    } else {
      logger.error(s"Could not correct all products, keep falling: ${stillIncorrect.size}")
    }
  }

  def restoreProductsRecursively(incorrect: Seq[Product], correct: Seq[Product] = Seq(), cnt: Int = 0): (Seq[Product], Seq[Product]) = {
    if (cnt == 10 || incorrect.isEmpty) (correct, incorrect)
    else {
      val (corrected, stillIncorrect) = incorrect
        .map { p =>
          logger.info(s"Try: ${p.url}")
          try {
            parseProduct(p.url)
          } catch {
            case _: Throwable =>
              logger.error("Failed...:(")
              p
          }
        }.partition(_.name.nonEmpty)
      restoreProductsRecursively(stillIncorrect, correct ++ corrected, cnt + 1)
    }
  }

  def parseMeta(doc: JsoupDocument, attrValue: String, attrName: String = "itemprop"): Option[String] = {
    doc >?> extractor("meta[" + attrName + "=" + attrValue + "]", attr("content"))
  }

  def toUrl(link: String) = if (link.startsWith("http")) link else baseUrl + link

  def parseProductUrls(productUrlsFile: String): Seq[String]

  def parseProductsFromFile(file: String, productsFile: String, drop: Int = 0, take: Int = 0): Unit = {
    parseProducts(Source.fromFile(file).getLines().toSeq, productsFile, drop, take)
  }

  def parseProducts(urls: Seq[String], productsFile: String, drop: Int = 0, take: Int = 0): Unit = {
    if (drop == 0) {
      deleteFileIfExists(productsFile)
    }
    val urlsChunk = if (drop != 0 && take != 0) urls.slice(drop, drop + take)
    else if (drop != 0) urls.drop(drop)
    else if (take != 0) urls.take(take)
    else urls

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
        val product = parseProduct(doc, url)
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

  protected def parseProduct(doc: JsoupDocument, url: String): Product

  protected def parseProduct(url: String): Product = {
    val doc = browser.get(url)
    parseProduct(doc, url)
  }
}

class ElementExt(e: Element) {
  def href: Option[String] = if (e.hasAttr("href")) {
    val baseHref = e.attr("href")
    val href = if (baseHref.startsWith("/") || baseHref.startsWith("http")) baseHref
    else "/" + baseHref
    Some(href)
  } else None
}

class WebElementExt(e: WebElement) {
  def href: Option[String] = Option(e.getAttribute("href"))
}