package org.epicsquad

case class Product(url: String, name: String = "", brand: String = "", category: Option[String] = None) {
  def toCsv = url + ";" + name + ";" + brand + ";" + category.getOrElse("")
}
