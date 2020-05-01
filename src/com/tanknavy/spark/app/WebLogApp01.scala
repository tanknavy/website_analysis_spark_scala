package com.tanknavy.spark.app

import java.util.regex.Matcher
import java.util.zip.CRC32
import java.util.{Date, Locale}

import com.tanknavy.spark.utils.UAUtils
import com.tanknavy.spark.utils.Utilities._
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.time.FastDateFormat
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.{HColumnDescriptor, HTableDescriptor, TableName}
import org.apache.hadoop.hbase.client.{Admin, Connection, ConnectionFactory, Put}
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.hbase.mapreduce.TableOutputFormat
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.internal.Logging
import org.apache.spark.sql.SparkSession

/**
 * 对日志进行ETL操作：把数据从文件系统（本地，HDFS）清洗（ip/ua/time）之后最终存储到Hbase中
 * 
 * 批处理： 每天凌晨处理昨天的数据
 * 时间：yyyyMMdd
 * HBase: logs_yyyyMMdd
 * 	创建表：表名和cf，每一行的列数可能不一样，稀疏性的表现
 * 	rowKey设计：
 * 		每行一个主键，一定要结合业务需求,通常是组合使用，时间作为rowkey的前缀字段（MD5/CRC32编码）
 *    cf: O
 *    column:把文件系统上解析出来的df的字段放到map中，一个循环拼成一个rowKey对应的cf
 *  
 *
 * 后续业务统计分析时，也是一天一个批次，
 * 
 */


object WebLogApp01 extends Logging{
  
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
        //var method:String = null //如果想要默认为空的话，记得写上类型，否则scala推测为Null类型
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
    
//    if(args.length !=1){ //生产环境中传入日期
//      println("Usage: WebLogApp01 <time>")
//      System.exit(1)
//    }
//    
//    val day = args(0)
    val day = "20200106"
    val input_prd = s"hdfs://localhost:8020/access/$day/*" // s函数将$var替代为实际值
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
      
      /*println(time) //[29/Nov/2015:03:50:05 +0000]
      FastDateFormat.getInstance("yyyyMMddHHmm").format(new Date(FastDateFormat.getInstance("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH).
        parse(time.substring(time.indexOf("[")+1, time.lastIndexOf("]"))).getTime()))
      */
    })

    // 在已有的DF之前添加或者修改字段，从time字段添加一个格式化后的formattime字段
    // 记得赋值才能更新，前面必须是var
    //logDF = logDF.withColumn("formattime", formatTime()(logDF("time"))) //闭包,
    schemaDF = schemaDF.withColumn("formattime", formatTime()(schemaDF("time"))) //闭包,
    //注意：df/ds直接map需要encoder
    schemaDF.show(false) //升序top20个
    
    // ---日志已经已经清洗完成，下一步写入HBase(那些字段属于哪个cf,表名，rowkey)
    val hbaseInfoRDD = schemaDF.rdd.map(x =>{ // rdd将Row映射
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
      columns.put("ip", ip)
      columns.put("formattime", formattime)
      columns.put("method", method)
      columns.put("url", url)
      columns.put("protocal", protocal)
      columns.put("status", status)
      columns.put("bytesent", bytesent)
      columns.put("refer", refer)
      columns.put("browserName", browserName)
      columns.put("browserVersion", browserVersion)
      columns.put("osName", osName)
      columns.put("osVersion", osVersion)

      // Hbase API put
      //val rowKey = refer
      val rowKey = getRowKey(day, refer+url+ip) //HBase一定要保证每个字段是唯一的
      val put = new Put(Bytes.toBytes(rowKey)) //要保存的HBase的Put对象
      
      //每一个rowkey对应的cf中所有的column字段
      for((k,v) <- columns){
        put.addColumn(Bytes.toBytes("O"), Bytes.toBytes(k.toString), Bytes.toBytes(v.toString)) // 列族，列名，值
      }
      
      (new ImmutableBytesWritable(rowKey.getBytes), put) //tuple键值对， 结果是这里报序列化错误,在spark里面使用kryo序列化
      
    })
    
    
    val conf= new Configuration()
    conf.set("hbase.rootdir","hdfs://localhost:8020/hbase")
    conf.set("hbase.zookeeper.quorum","localhost:2181")
    
    val tableName = createTable(day, conf)
    

    conf.set(TableOutputFormat.OUTPUT_TABLE,tableName)
    //设置写数据到哪个表中
    hbaseInfoRDD.saveAsNewAPIHadoopFile( // path, key class, value class, outputFormat class
        "", //具体Hbase路径
        classOf[ImmutableBytesWritable],
        classOf[Put],
        classOf[TableOutputFormat[ImmutableBytesWritable]], //输出格式的类型[key类型]
        conf
        )
    
    logInfo(s"this batch($day) is finished")
    
    spark.stop()
    
  }
  
  // 创建HBse表
  def createTable(day:String,conf:Configuration) ={
    val table = "access_" + day
    var conection:Connection = null //不写类型时scala推测为connection:Null类型,会出现类型不匹配错误
    var admin:Admin =null
    
    try {
      conection = ConnectionFactory.createConnection(conf)
      admin = conection.getAdmin
      
      val tableName = TableName.valueOf(table)
      if(admin.tableExists(tableName)){ // 表一天一个
         admin.disableTable(tableName)
         admin.deleteTable(tableName)
      }
      
      val tableDesc = new HTableDescriptor(tableName)
      val columnDesc = new HColumnDescriptor("O")
      tableDesc.addFamily(columnDesc)
      admin.createTable(tableDesc)
      
    } catch {
      case t: Exception => t.printStackTrace() // TODO: handle error
    } finally {
      if(null != admin){
        admin.close()
      }
      if(null != conection){
        conection.close()
      }
    }
    
    table 
    
  }
  
  
  // hbase rowkey设计，使用MD5或者CRC32
  def getRowKey(time:String, info:String) = { //时间_重要字段编码
    /**
     * 由于rowkey采用time_crc32(info)进行拼接，
     * 只要是字符串拼接，尽量不要使用加号+，面试题
     * StringBuffer vs StringBuilder，线程安全和线程不安全
     */
    val builder = new StringBuilder(time)
    builder.append("_")
    
    val crc32 = new CRC32()
    crc32.reset()
    if(StringUtils.isNotEmpty(info)){
      crc32.update(Bytes.toBytes(info))
    }
    
    builder.append(crc32.getValue) // 得到固定Long类型
    builder.toString()
    
  }
  
  
}