package org.epicsquad

case class Product(url: String, name: String = "", brand: Option[String] = None, category: Option[String] = None) {
  def toCsv = url + ";" + name + ";" + brand.getOrElse("") + ";" + category.getOrElse("")
}
