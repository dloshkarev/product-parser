package org.epicsquad

case class Product(url: String, name: String = "", brand: Option[String] = None, category: Option[String] = None) {
  def toCsv: String = url + ";" + name + ";" + brand.getOrElse("") + ";" + category.getOrElse("")
  def toExport: String = name + ";" + brand.getOrElse("") + ";" + category.getOrElse("")
}

object Product {

  implicit def stringToOptionString(s: String): Option[String] = if (s.trim.isEmpty) None else Some(s)

  def fromCsv(s: String): Product = {
    val Array(url, name, brand, category, _*) = s.split(";", -1)
    Product(url, name, brand, category)
  }
}