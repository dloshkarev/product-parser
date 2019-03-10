package org.epicsquad

import java.util.UUID

case class Product(source: String, url: String, name: String = "", brand: Option[String] = None, category: Option[String] = None) {
  def toCsv: String = url + ";" +  source + ";" + clean(name) + ";" + clean(brand.getOrElse("")) + ";" + clean(category.getOrElse(""))
  def toExport: String = source + ";" + clean(name) + ";" + clean(brand.getOrElse("")) + ";" + clean(category.getOrElse(""))
  def toMerge: String = UUID.randomUUID() + ";" + clean(name) + ";0.0.0.0;неклассифицированный/неклассифицированный/неклассифицированный/неклассифицированный;parse;;" + source + ";;" + clean(brand.getOrElse("")) + ";;;;;;;"
  private def clean(s: String) = s.replaceAll(";", " ").trim
}

object Product {
  private val CSV_REGEX = ";(?=([^\"]*\"[^\"]*\")*[^\"]*$)"

  implicit def stringToOptionString(s: String): Option[String] = if (s.trim.isEmpty) None else Some(s)

  def fromCsv(s: String, n: Int): Product = {
    try {
      val Array(url, source, name, brand, category, _*) = s.split(CSV_REGEX, -1)
      Product(source, url, name, brand, category)
    } catch {
      case e: Throwable =>
        throw new RuntimeException(s"Cannot parse line $n: $s", e)
    }
  }

  def fromExportCsv(s: String, n: Int): Product = {
    try {
      val Array(source, name, brand, category, _*) = s.split(CSV_REGEX, -1)
      Product(source, "", name, brand, category)
    } catch {
      case e: Throwable =>
        throw new RuntimeException(s"Cannot parse line $n: $s", e)
    }
  }
}