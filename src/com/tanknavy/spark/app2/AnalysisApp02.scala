package com.tanknavy.spark.app2

import java.sql.DriverManager
import java.util.Properties

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.CellUtil
import org.apache.hadoop.hbase.client.{Result, Scan}
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.hbase.mapreduce.TableInputFormat
import org.apache.hadoop.hbase.protobuf.ProtobufUtil
import org.apache.hadoop.hbase.util.{Base64, Bytes}
import org.apache.spark.sql.SparkSession

import scala.util.{Failure, Success, Try}

/**
 * 使用Spark对Hbase中的数据做统计分析操作
 * 1) 统计每个国家，每个身份的访问量
 * 2）统计不同浏览器的访问量
 */
object AnalysisApp02 {
  
  def main(args: Array[String]): Unit = {
    
    val spark = SparkSession.
      builder().
      config("spark.serizlizer","org.apache.spark.serializer.KryoSerializer").
      master("local[2]").
      appName("AnalysisApp01").
      getOrCreate()
    
    //获取要进行统计分析的日期
    val day = "20200106"
    
    val conf = new Configuration()
    conf.set("hbase.rootdir","hdfs://localhost:8020/hbase")
    conf.set("hbase.zookeeper.quorum","localhost:2181")
    
    val tablename = "access_" + day
    conf.set(TableInputFormat.INPUT_TABLE,tablename) //从哪个表里面去读数据
    
    val scan = new Scan() //Hbase查询数据
    
    // 设置要查询的cf
    scan.addFamily(Bytes.toBytes("O"))
    // 设置要查询的列(这次分析国家和省份的访问量)
    scan.addColumn(Bytes.toBytes("O"), Bytes.toBytes("country")) // cf，列名
    scan.addColumn(Bytes.toBytes("O"), Bytes.toBytes("province")) // cf，列名
    
    scan.addColumn(Bytes.toBytes("O"), Bytes.toBytes("browsername")) // 浏览器栏位
    // 设置scan
    conf.set(TableInputFormat.SCAN, Base64.encodeBytes(ProtobufUtil.toScan(scan).toByteArray()) ) //常量 SCAN = "hbase.mapreduce.scan";
    
    // 通过Spark的
    // 写入hbase时 classOf[ImmutableBytesWritable],classOf[Put]，classOf[TableOutputFormat]
    // 那么读入时
    val hbaseRDD = spark.sparkContext.newAPIHadoopRDD(conf, classOf[TableInputFormat], classOf[ImmutableBytesWritable], classOf[Result])
    
    // 测试一下, 正式环境要注释掉
    hbaseRDD.take(10).foreach(x => { // (key,value)
      val rowKey = Bytes.toString(x._1.get()) //rowKey
      //value是列值
      for( cell <- x._2.rawCells()){
        val cf = Bytes.toString(CellUtil.cloneFamily(cell))
        val qualifier = Bytes.toString(CellUtil.cloneQualifier(cell))
        val value = Bytes.toString(CellUtil.cloneValue(cell))
        
        println(s"$rowKey: $cf : $qualifier : $value ") // 20191208_123456, O, contry, usa
      }
    })
    
    /**
     * spark 优化：常用RDD缓存对迭代算法,LRU算法自动删除，rdd.unpersist删除缓存
     */
    hbaseRDD.cache()
    
    // 需求一：统计每个国家，地区 ==> top10
    hbaseRDD.map(x =>{
      val country = Bytes.toString(x._2.getValue("O".getBytes, "country".getBytes)) //hbase中cf为O,列名是国家
      val province = Bytes.toString(x._2.getValue("O".getBytes, "province".getBytes)) //hbase中cf为O,列名是地区
      
      ((country, province), 1) // k/v的tuple在RDD中
    }).reduceByKey(_+_)
      .map(x =>(x._2,x._1)).sortByKey(false) //降序
      .map(x =>(x._2,x._1)).take(10) //国家地区排名
      .foreach(println)
    
   // 需求二：统计浏览器
   hbaseRDD.map(x =>{
      val browsername = Bytes.toString(x._2.getValue("O".getBytes, "browsername".getBytes)) //hbase中cf为O,列名是国家
       
      (browsername,1) // k/v的tuple在RDD中
    }).reduceByKey(_+_)
      .map(x =>(x._2,x._1)).sortByKey(false) //降序
      .map(x =>(x._2,x._1))
      .foreach(println) //浏览器排序
    
    
    //以上两个需求如果改为Spark SQL方式实现, 函数时，也可以先注册为临时表再sql查询
    import spark.implicits._ //隐式转换rdd为dataset/dataframe
    hbaseRDD.map(x =>{
      val country = Bytes.toString(x._2.getValue("O".getBytes, "country".getBytes)) //hbase中cf为O,列名是国家
      val province = Bytes.toString(x._2.getValue("O".getBytes, "province".getBytes)) //hbase中cf为O,列名是地区
      
      CountryProvince(country,province) //返回带有列表case class的对象
    }).toDF.select("country","province").groupBy("country","province").count().orderBy($"count".desc).show(false) // 分组统计
    
    hbaseRDD.map(x =>{
      val browsername = Bytes.toString(x._2.getValue("O".getBytes, "browsername".getBytes)) //hbase中cf为O,列名是国家
      
      Browser(browsername)
    }).toDF.select("browsername").groupBy("browsername").count().show(false)
    
    //注册临时表方式
    hbaseRDD.map(x =>{
      val country = Bytes.toString(x._2.getValue("O".getBytes, "country".getBytes)) //hbase中cf为O,列名是国家
      val province = Bytes.toString(x._2.getValue("O".getBytes, "province".getBytes)) //hbase中cf为O,列名是地区
      
      CountryProvince(country,province) //返回带有列表case class的对象
    }).toDF.createOrReplaceTempView("location") //注册为临时表
    
    spark.sql("select country,province, count(1) cnt from location group by country,province ordery by cnt desc").show(false)
    
    
    // 统计结果写入mySQL
    val resultRDD = hbaseRDD.map(x =>{
      val browsername = Bytes.toString(x._2.getValue("O".getBytes, "browsername".getBytes)) //hbase中cf为O,列名是国家
       
      (browsername,1) // k/v的tuple在RDD中
    }).reduceByKey(_+_)
      .map(x =>(x._2,x._1)).sortByKey(false) //降序
      .map(x =>(x._2,x._1))
   
      
   resultRDD.coalesce(2).foreachPartition(part =>{ //每个分区,这里数据量很少，是统计结果，可以考虑coalesce
   //resultRDD.foreachPartition(part =>{ //每个分区
     Try{
       // 结果写入mysql
       val conn = {
         
         // JDBC写入mySQL
         Class.forName("com.mysql.jdbc.Driver") //装载library
         val url = "jdbc:mysql://localhost:3306/db?characterEncoding=UTF-8"
         val user ="root"
         val password = "root"
         DriverManager.getConnection(url,user,password)
       }
       val sql = "insert into browser_stat(day,browser,cnt) values(?,?,?)"
       val pstms = conn.prepareStatement(sql) 
       
       pstms.addBatch(s"delete from browser_stat where day=$day") //没有主键，避免重复，考虑先清除记录
       
       val preAutoCommit = conn.getAutoCommit //默认自动提交
       conn.setAutoCommit(false)
       
       part.foreach(x =>{ //一个分区里面每个记录
         pstms.setString(1, day)
         pstms.setString(2, x._1)
         pstms.setInt(3, x._2)
         
         pstms.addBatch()
       })
       
       pstms.executeBatch() //批量提交
       conn.commit()
       
       
       (conn,preAutoCommit)
       
     } match{
       case Success((conn,preAutoCommit)) => {
         conn.setAutoCommit(preAutoCommit) //还原成自动提交模式
         if(conn != null) conn.close() //成功就关闭
       }
       case Failure(e) => throw e
     }
   })
   
   
   // DataFrame/DataSet数据通过format("jdbc")直接写入MySQL
   //https://spark.apache.org/docs/2.3.0/sql-programming-guide.html#save-modes
   val url = "jdbc:mysql://localhost:3306/db?characterEncoding=UTF-8"
   val prop = new Properties()
   prop.setProperty("user", "root")
   prop.setProperty("password", "value")
   //默认ErrorIfExists
   resultRDD.toDF().write.mode("overwrite").jdbc("jdbc:mysql://localhost:3306/db?characterEncoding=UTF-8", "browser_stat", prop)
   
   
    spark.stop()
  }
  
  // spark sql时使用
  case class CountryProvince(country:String, province:String) //case class会自动创建构造函数和访问方法
  case class Browser(browsername:String)
  
}