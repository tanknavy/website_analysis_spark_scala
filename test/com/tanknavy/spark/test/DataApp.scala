package com.tanknavy.spark.test
import com.tanknavy.spark.app.WebLogApp01

object DataApp {
  
  def main(args: Array[String]): Unit = {
    val url = "GET /robots.txt HTTP/1.1"
    val ip = "66.249.75.159"
    val refer = "http://www.google.com/bot.html"
    val ua = "" //实际中再加ua
    
    val rowkey = WebLogApp01.getRowKey("20200106", url+refer+ip)
    println(rowkey) // 20190106_3044202596
    
  }
}