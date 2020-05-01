Flume->HFDS->Spark->Hbase->Spark->MySQL

2.6.0-cdh5.15.1
开发：
部署：spark-2.4.2-bin-2.6.0-cdh5.15.1.tgz
maven仓库默认repository路径.m2

<localRepository>d:\repository</localRepository>

UserAgent进行处理和统计分析
	github里面UASparser解析操作系统，浏览器信息
	

统计各个省份，地市的信息
	需要根据IP解析
		开源：纯真（自己尝试）
		生产：付费，会定时更新IP库，直接调用IP解析API
	调用Spark的方法，内部实现集成

Spark:
	日志按照统计需求清洗到Hbase表中
		log => df
		df => put
		spark把put写入到Hbase中
	
	对Hbase表中的数据进行维度指标的统计分析操作
		Spark读取HBase的Result读取出来
			使用RDD
			使用DataFrame API
			使用Spark SQL API

版本一：Put写数据
			
版本二：上线和优化
 	1)上线运行：local,standalone,yarn, mesos, k8s
 	2)ETL代码优化, 思路：spark第一次ETL结果写入hfile再load到HBase，Put方式每条记录效率很低
 	3)结果写入mySQL数据库
	
	QA: Spark on Yarn的执行流程以及运行方式的区别(client vs cluster)
	./spark-shell --master yarn  // spark运行在yarn
	使用assembly插件打包时，将spark和hadoop标识为provicded依赖，这些不需要被bundle，因为cluster manager会提供，没有的jar用--jars提供
	./bin/spark-submit --class main-class --master <master-url> --deploy-mode <client/cluster> --conf <k>=<v> app.jar [app-args]
	
	使用Yarn时：确保要么HADOOP_CONF_DIR=/home/hadoop/app/hadoop-2.8.3/etc/hadoop,要么YARN_CONF_DIR
	mvn clean package -DskipTests
	scp pc user@server
	Linux:> date -d"yesterday" +"%F %H:%M" 获取昨天时间， 简写> date -d"1 day ago" +"%Y%m%d"
	cron: 3 0 * * * /home/spark/job
	
	conf: "spark.serizlizer","org.apache.spark.serializer.KryoSerializer
	appname:
	master:
	
	
	linux shell脚本：
	export HADOOP_CONF_DIR=/home/hadoop/app/hadoop-2.8.3/etc/hadoop
	$HADOOP_HOME/sbin/start-yarn.sh //启动yarn
	
	$SPARK_HOME/bin/spark-submit \
	--class com.tanknavy.spark.WebLogApp02 \
	--master yarn \
	--name WebLogApp02 \
	--conf "spark.serizlizer=org.apache.spark.serializer.KryoSerializer" \
	--packages cz.mallat.uasparser:uasparser:0.6.2	\	//添加UA依赖包新方式，在POM中所需g/a/v，运行时会去中央仓库下载到.ivy2
	--jars $(echo $HBASE_HOME/lib/*.jar | tr ' ' ',') \	//添加Hbase依赖包，tr将空格符转换为逗号分隔
	/home/hadoop/jars/spark-job/myApp.jar $(date -d"1 day ago" +"%Y%m%d")
	
	yarn杀掉作业： yarn application -kill application_12345_0000 //从hadoop作业8088端口查看
	如何使用spark on yarn申请资源提速
	
	
	    
    // 统计结果写入mySQL
    1)pom.xml添加sql Driver
    2)创建mysql表
    3）RDD=>M
    create table if not exists browser_stat(
    day varchar(10) not null,
    browername varchar(100) not null,
    cnt int
    ) engine=innodb default charset=utf8;
    // 统计结果DataFrame采用df.wrirte().jdbc()直接写入mySQL
    
    
版本三：HBase写入优化(WAL, HFile)
	ETL整个过程还是存在一些低效问题 df.rdd.map(x => row->Put->conf.set(TableOuputFormat.OUTPUT_TABLE,tablename))
	HBase:
		WAL: write ahead log预写日志，用于恢复memestore中还没有刷新到磁盘中的数据，默认开启
	
	不写WAL,手工刷新memstore到磁盘admin.flush(tablename)
	
	继续优化HBase写入：HFile是HBase底层的储存数据格式，能否使用Spark将DF/RDD的数据生成HFile，然后load到HBase里面?
	Bulk Loading:使用HFileOUtputFormat2
	
	
	既然可以直接生成HFile再load到HBase，那么后面Spark是否可以直接查询HFile文件来分析呢？
	
	
	
		
