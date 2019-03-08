package org.epicsquad.parsers

import java.io.FileWriter
import java.net.URL

import com.typesafe.scalalogging.StrictLogging
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.Element
import org.epicsquad.Settings
import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}

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

  def parseMeta(doc: JsoupDocument, attrValue: String, attrName: String = "itemprop"): Option[String] = {
    doc >?> extractor("meta[" + attrName + "=" + attrValue + "]", attr("content"))
  }

  def parseProductUrls(productUrlsFile: String): Seq[String]

  def parseProductsFromFile(file: String, productsFile: String): Unit = {
    parseProducts(Source.fromFile(file).getLines().toSeq, productsFile)
  }

  protected def parseProducts(urls: Seq[String], productsFile: String): Unit
}

class ElementExt(e: Element) {
  def href: Option[String] = if (e.hasAttr("href")) Some(e.attr("href")) else None
}

class WebElementExt(e: WebElement) {
  def href: Option[String] = Option(e.getAttribute("href"))
}