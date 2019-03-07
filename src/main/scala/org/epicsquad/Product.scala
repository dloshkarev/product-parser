package org.epicsquad

case class Product(name: String, category: Option[String]) {
  def toCsv = name + ";" + category.getOrElse("")
}
