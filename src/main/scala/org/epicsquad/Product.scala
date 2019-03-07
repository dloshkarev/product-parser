package org.epicsquad

case class Product(url: String, name: String = "", category: Option[String] = None) {
  def toCsv = url + ";" + name + ";" + category.getOrElse("")
}
