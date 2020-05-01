package com.tanknavy.spark.utils

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileStatus
import org.apache.hadoop.mapreduce.Job
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.execution.datasources.{FileFormat, OutputWriterFactory, PartitionedFile}
import org.apache.spark.sql.sources.Filter
import org.apache.spark.sql.types.{StringType, StructField, StructType}
import org.apache.spark.unsafe.types.UTF8String

/**
 * spark.read.format("") //数据输入处理，自定义格式
 * https://stackoverflow.com/questions/39401577/create-spark-data-frame-from-custom-data-format
 * https://jaceklaskowski.gitbooks.io/mastering-spark-sql/spark-sql-DataFrameReader.html#text
 * https://github.com/apache/spark/blob/master/sql/core/src/main/scala/org/apache/spark/sql/execution/datasources/FileFormat.scala
 * https://github.com/jaceklaskowski/spark-workshop/tree/master/solutions/spark-mf-format
 * https://stackoverflow.com/questions/39401577/create-spark-data-frame-from-custom-data-format
 */
class DefaultSource extends FileFormat {
  
  override def inferSchema(sparkSession: SparkSession, options: Map[String, String], files: Seq[FileStatus]): Option[StructType] = {
    println(s">>> inferSchema($files)")
    Some(StructType(
      StructField("line", StringType, nullable = true) :: Nil
    ))
  }

  override def prepareWrite(sparkSession: SparkSession, job: Job, options: Map[String, String], dataSchema: StructType): OutputWriterFactory = {
    println(">>> prepareWrite")
    null
  }

  override def buildReader(sparkSession: SparkSession, dataSchema: StructType, partitionSchema: StructType, requiredSchema: StructType, filters: Seq[Filter], options: Map[String, String], hadoopConf: Configuration): (PartitionedFile) => Iterator[InternalRow] = {
    pf => Iterator(InternalRow(UTF8String.fromString("hello")))
  }
}