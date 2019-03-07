package org.epicsquad

import java.net.URL

import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.model.Element
import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}

trait ProductParser {
  val browser = new JsoupBrowser
  private val capabilities = DesiredCapabilities.chrome()
  implicit val webDriver: RemoteWebDriver = new RemoteWebDriver(new URL(Settings.seleniumUrl), capabilities)

  val baseUrl: String

  implicit def elementToExt(e: Element): ElementExt = new ElementExt(e)
  implicit def webElementToExt(e: WebElement): WebElementExt = new WebElementExt(e)

  def prepareUrls(elems: Seq[Element]) = elems.flatMap(_.href).filterNot(_ == "#").distinct

  def parse(partsDirectory: String, productUrlsFile: String): Seq[Product]
}

class ElementExt(e: Element) {
  def href: Option[String] = if (e.hasAttr("href")) Some(e.attr("href")) else None
}

class WebElementExt(e: WebElement) {
  def href: Option[String] = Option(e.getAttribute("href"))
}