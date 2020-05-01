Spark Streaming实时处理

项目架构及处理流程

批量：HDFS=>Spark=>Hbase=>Spark=>Mysql=>UI
实时：Log=>Flume=>Kafla=>SparkStreaming(Direct/Receiver)=>Redis=>UI

大数据团队分工：采集，批处理，实时处理，API，前端

项目需求
1)统计每天付费成功的总订单数，订单总金额
2)统计每小时付费成功的总订单数，订单总金额
3)统计每分钟付费成功的总订单数，订单总金额
4）基于Window付费成功的总订单数，订单金额
5）付费订单占总下单的占比：天，小时，分钟
