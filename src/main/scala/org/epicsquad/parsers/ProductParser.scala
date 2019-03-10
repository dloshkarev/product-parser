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
import org.epicsquad.{Product, ProxyData, Settings}
import org.jsoup.HttpStatusException
import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.mutable
import scala.io.Source
import scala.reflect.io.File
import scala.util.Random

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
  protected val source: String

  private var proxyList = Array.empty[ProxyData]

  private def rotateProxy: Unit = {
    if (proxyList.isEmpty) {
      val proxyData = Source.fromResource("proxy.txt").getLines().toSeq.distinct.map { line =>
        val Array(host, port) = line.split(":", -1)
        ProxyData(host, port)
      }.toArray
      logger.info(s"Added ${proxyData.length} proxies from file")
      proxyList = proxyData
    }

    val proxy = proxyList(Random.nextInt(proxyList.length - 1))
    System.setProperty("http.proxyHost", proxy.host)
    System.setProperty("http.proxyPort", proxy.port)
  }

  protected def getProxyDoc(url: String): JsoupDocument = {
    try {
      rotateProxy
      Thread.sleep(Random.nextInt(5000))
      browser.get(url)
    } catch {
      case _: HttpStatusException =>
        getProxyDoc(url)
    }
  }

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

  def parse(source: String, productUrlsFile: String, productsFile: String): Unit = {
    logger.info("Parsing urls started")
    val productUrls = parseProductUrls(productUrlsFile)
    logger.info(s"Parsing urls finished. Stored ${productUrls.size} into $productUrlsFile")
    logger.info("Parsing products started")
    parseProducts(source, productUrls, productsFile)
    logger.info(s"Parsing products finished. Stored into $productsFile")
    if (webDriverOpt.nonEmpty) {
      webDriver.close()
    }
  }

  def getDiff(productFile: String, urlFile: String, diffFile: String): Unit = {
    deleteFileIfExists(diffFile)
    val productUrls = Source.fromFile(productFile).getLines().map(_.split(";", -1)(0)).toSet
    val urls = Source.fromFile(urlFile).getLines().toSet
    val diff = urls -- productUrls
    logger.info(s"Found ${diff.size} diffs")
    Files.write(Paths.get(diffFile), diff.toSeq.asJava)
  }

  def exportProducts(productFile: String, outFile: String): Unit = {
    deleteFileIfExists(outFile)

    val products = Source.fromFile(productFile).getLines()
      .map(_.replaceAll("\"", "").trim)
      .zipWithIndex
      .toSeq
      .map { case (line, n) => Product.fromCsv(line, n) }

    val (correct, incorrect) = products.partition(_.name.nonEmpty)

    val (corrected, stillIncorrect) = if (incorrect.nonEmpty) {
      logger.info(s"Found ${incorrect.size} incorrect lines. Try to fix...")
      restoreProductsRecursively(incorrect, correct)
    } else (correct, Seq())

    if (stillIncorrect.isEmpty) {
      val unique = corrected.groupBy(p => (p.name, p.brand)).map(_._2.head).toSeq
      logger.info(s"Found ${unique.size} unique products from ${corrected.size}")

      logger.info(s"${unique.size} products added into: $productFile")
      Files.write(Paths.get(outFile), unique.map(_.toExport).asJava)
      logger.info(s"${unique.size} products exported into: $outFile")
    } else {
      logger.error(s"Could not correct all products, keep falling: ${stillIncorrect.size}")
    }

    Files.write(Paths.get(productFile), (corrected ++ stillIncorrect).map(_.toCsv).asJava)
  }

  def mergeFormatInclude(currentCatalog: String, csvDirectory: String, outFile: String): Unit = {
    deleteFileIfExists(outFile)
    val dir = new java.io.File(csvDirectory)
    if (dir.exists && dir.isDirectory) {
      val files = dir.listFiles.filter(_.isFile)
      val newLines = files.foldLeft(Seq.empty[String]) { (acc, file) =>
        acc ++ Source.fromFile(file).getLines()
      }.zipWithIndex.map { case (s, i) =>
        Product.fromExportCsv(s, i)
      }
      .groupBy(p => (p.name, p.brand)).map(_._2.head)
      .map(_.toMerge)
      logger.info(s"Total merged lines: ${newLines.size}")

      val totalLines = Source.fromFile(currentCatalog).getLines().toSeq ++ newLines
      Files.write(Paths.get(outFile), totalLines.asJava)
      logger.info(s"Total lines including current catalog: ${totalLines.size}. Saved into: $outFile")
    } else {
      throw new RuntimeException("Incorrect csv directory!")
    }
  }

  def restoreProductsRecursively(incorrect: Seq[Product], correct: Seq[Product] = Seq(), cnt: Int = 0): (Seq[Product], Seq[Product]) = {
    if (cnt == 3 || incorrect.isEmpty) (correct, incorrect)
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
    parseProducts(source, Source.fromFile(file).getLines().toSeq.distinct, productsFile, drop, take)
  }

  def parseProducts(source: String, urls: Seq[String], productsFile: String, drop: Int = 0, take: Int = 0): Unit = {
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
          val product = Product(source, url)
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