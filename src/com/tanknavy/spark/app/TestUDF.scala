package com.tanknavy.spark.app
import org.apache.spark.sql.SparkSession
import org.apache.commons.lang3.time.FastDateFormat
import java.util.Locale
import java.util.Date
import java.lang.Long
import java.util.regex.Matcher

import com.tanknavy.spark.utils.Utilities._
import com.tanknavy.spark.utils.UAUtils
import scala.collection.immutable.Nil
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.spark.SparkConf

object TestUDF {
  
  case class Log(time:String, browserName:String, browserVersion:String, osName:String, osVersion:String)
  case class WebLog(ip:String, time:String, method: String, url:String, protocal: String, status:String, bytesent:String, refer:String,
      browserName:String, browserVersion:String, osName:String, osVersion:String)
 
  def mapperLog(line:String): Log = {
    val pattern = apacheLogPattern()
    val matcher: Matcher = pattern.matcher(line)
      
      if(matcher.matches()){
        val dateTime = matcher.group(4)
        //val ua = matcher.group(9).toString().split(",") //UserAgent
        var ua = matcher.group(9).toString()
        val uaBean = UAUtils.getUserAgentInfo(ua)
        
        val bn = uaBean.getBrowserName
        val bv = uaBean.getBrowserVersion
        val on = uaBean.getOsName
        val ov = uaBean.getOsVersion
        
        val log:Log = Log(dateTime,bn,bv,on,ov)
        return log
      }else {
        return null //The Empty values in Scala are represented by Null, null, Nil, Nothing, None, and Unit.
      }
  }
  
  def mapperWebLog(line:String): WebLog = {
    val pattern = apacheLogPattern()
    val matcher: Matcher = pattern.matcher(line)
      
      if(matcher.matches()){
        val ip = matcher.group(1)
        val time = matcher.group(4)
        //val ua = matcher.group(9).toString().split(",") //UserAgent
        val request = matcher.group(5).toString().split(" ")
        
        var method = "unknown" //如果解析失败，默认值
        var url = "unknown"
        var protocol = "unknown"
        if(request.length == 3){
          method = request(0)
          url = request(1)
          protocol = request(2)
        }
        
        val status = matcher.group(6)
        val bytes = matcher.group(7)
        val referer = matcher.group(8)
        
        var ua = matcher.group(9).toString()
        val uaBean = UAUtils.getUserAgentInfo(ua)
        val bn = uaBean.getBrowserName
        val bv = uaBean.getBrowserVersion
        val on = uaBean.getOsName
        val ov = uaBean.getOsVersion
        
        val log:WebLog = WebLog(ip,time, method, url, protocol, status, bytes, referer, bn,bv,on,ov)
        return log
      }else {
        return null //The Empty values in Scala are represented by Null, null, Nil, Nothing, None, and Unit.
      }
  }
  
  
  
  def main(args: Array[String]): Unit = {
    val day = "20200106"
    val input2 = s"hdfs://localhost:8020/access/$day/*" // s函数将$var替代为实际值
    val input = "E:/Project/Spark/input"
    
    
    val spark = SparkSession.
      builder().
      config("spark.serizlizer","org.apache.spark.serializer.KryoSerializer").
      master("local[2]").
      getOrCreate()
    //val sparkConf = new SparkConf()
    //sparkConf.set("spark.serizlizer","org.apache.spark.serializer.KryoSerializer")
    

    //或者自定义类型，或者先已sparkContext文本格式读入，在mapper为DF
    //var logDF = spark.read.format("com.tanknavy.customer.format").option("path", input).load()
    //https://jaceklaskowski.gitbooks.io/mastering-spark-sql/spark-sql-DataFrameReader.html#json
    //https://github.com/jaceklaskowski/spark-workshop/tree/master/solutions/spark-mf-format
    val pattern = apacheLogPattern()
    
    //var logDF = spark.read.text("E:/Project/Spark/input") // DataFrame Row
    val logDF = spark.sparkContext.textFile("E:/Project/Spark/input/access_log.txt") //RDD[String]
    val formatLogDF = logDF.map(mapperWebLog).filter(x => x != null) //如果不过滤返回就是Any类型，这里泛型需要Log
    
    import spark.implicits._
    var schemaDF = formatLogDF.toDF()
    schemaDF.printSchema()
    schemaDF.show(false)

    /**
     * Spark SQL自定义函数的使用
     */
    import org.apache.spark.sql.functions._
    def formatTime() = udf((time: String) => { //引入UDF函数
      // [29/Nov/2015:03:50:05 +0800] 时间格式转换 [yyyyMMddHHmm]
      //SimpleDataFormat线程不安全

      val Str2Long = FastDateFormat.getInstance("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH).
        parse(time.substring(time.indexOf("[")+1, time.lastIndexOf("]"))).getTime() // 去掉[]拿到long类型就是timestamp

      FastDateFormat.getInstance("yyyyMMddHHmm").format(new Date(Str2Long)) //转换成yyyyMMddHHmm
      /*
      println(time) //[29/Nov/2015:03:50:05 +0000]
      FastDateFormat.getInstance("yyyyMMddHHmm").format(new Date(FastDateFormat.getInstance("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH).
        parse(time.substring(time.indexOf("[")+1, time.lastIndexOf("]"))).getTime()))
      */
    })

    // 在已有的DF之前添加或者修改字段，从time字段添加一个格式化后的formattime字段
    // 记得赋值才能更新，前面必须是var
    //logDF = logDF.withColumn("formattime", formatTime()(logDF("time"))) //闭包,
    schemaDF = schemaDF.withColumn("formattime", formatTime()(schemaDF("time"))) //闭包,
    
    schemaDF.show(false) //升序top20个
    
    // ---日志已经已经清洗完成，下一步写入HBase(那些字段属于哪个cf,表名，rowkey)
    schemaDF.rdd.map(x =>{ // rdd将Row映射
      val ip = x.getAs[String]("ip")
      val formattime = x.getAs[String]("formattime")
      val method = x.getAs[String]("method")
      val url = x.getAs[String]("url")
      val protocal = x.getAs[String]("protocal")
      val status = x.getAs[String]("status")
      val bytesent = x.getAs[String]("bytesent")
      val refer = x.getAs[String]("refer")
      val browserName = x.getAs[String]("browserName")
      val browserVersion = x.getAs[String]("browserVersion")
      val osName = x.getAs[String]("osName")
      val osVersion = x.getAs[String]("osVersion")
       
      // Hbase列映射
      val columns = scala.collection.mutable.HashMap[String,String]()
      columns.put("ip", "ip")
      columns.put("formattime", "formattime")
      columns.put("method", "method")
      columns.put("url", "url")
      columns.put("protocal", "protocal")
      columns.put("status", "status")
      columns.put("bytesent", "bytesent")
      columns.put("refer", "refer")
      columns.put("browserName", "browserName")
      columns.put("browserVersion", "browserVersion")
      columns.put("osName", "osName")
      columns.put("osVersion", "osVersion")

      // Hbase API put
      val rowKey = refer
      val put = new Put(Bytes.toBytes(rowKey))
      for((k,v) <- columns){
        put.addColumn(Bytes.toBytes(""), Bytes.toBytes(k.toString), Bytes.toBytes(v.toString)) // 列族，列名，值
      }
      
      (new ImmutableBytesWritable(rowKey.getBytes), put) //结果是这里报序列化错误,在spark里面使用kryo序列化
      
    })
    
    spark.stop()
  }
}