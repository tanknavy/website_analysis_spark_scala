package com.tanknavy.spark.utils
import com.typesafe.config.ConfigFactory

/**
 * 在spark_www开发中使用java.uti.Properties来读取配置
 * 这里使用com.typesafe的config来读取配置
 */
object ParamsConf {
  
  private lazy val config = ConfigFactory.load() //lazy修饰直到第一次访问时才执行
  
  val topic = config.getString("kafka.topic") //默认从application.confg中读取
  
  def main(args: Array[String]): Unit = {
    println(ParamsConf.topic)
    printl
    
   
  }
   
}