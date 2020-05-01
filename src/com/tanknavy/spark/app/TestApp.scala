package com.tanknavy.spark.app

import org.apache.spark.sql.SparkSession

/**
 * 测试Hbase和Spark整合的兼容性
 */
object TestApp {
   def main(args: Array[String]): Unit = {
     val spark = SparkSession.builder().appName("TestApp").master("local[2]").
       config("spark.sql.warehouse.dir","E:/input/spark/warehouse").
       getOrCreate()
     
     val rdd = spark.sparkContext.parallelize(List(1,2,3,4))
     rdd.collect().foreach(println)
     spark.stop()
   }
}