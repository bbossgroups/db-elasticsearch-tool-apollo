package org.frameworkset.elasticsearch.imp.quartz;

import org.frameworkset.tran.ExportResultHandler;
import org.frameworkset.tran.config.ImportBuilder;
import org.frameworkset.tran.metrics.TaskMetrics;
import org.frameworkset.tran.plugin.db.input.DBInputConfig;
import org.frameworkset.tran.plugin.db.output.DBOutputConfig;
import org.frameworkset.tran.schedule.CallInterceptor;
import org.frameworkset.tran.schedule.ExternalScheduler;
import org.frameworkset.tran.schedule.TaskContext;
import org.frameworkset.tran.schedule.quartz.BaseQuartzDatasynJob;
import org.frameworkset.tran.task.TaskCommand;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

/**
 * 原生的数据同步quartz作业调度任务
 */
public class ImportDataJob extends BaseQuartzDatasynJob {

    public void init(){
        externalScheduler = new ExternalScheduler();
        externalScheduler.dataStream((Object params)->{
            JobExecutionContext context = (JobExecutionContext)params;
            ImportBuilder importBuilder = ImportBuilder.newInstance();
            String insertsql = "INSERT INTO cetc ( age, name, create_time, update_time)\n" +
                    "VALUES ( #[age],  ## 来源dbdemo索引中的 operModule字段\n" +
                    "#[name], ## 通过datarefactor增加的字段\n" +
                    "#[create_time], ## 来源dbdemo索引中的 logContent字段\n" +
                    "#[update_time]) ## 通过datarefactor增加的地理位置信息字段";
            // 获取作业参数
            JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
            Object data = jobDataMap.get("aa");
            //指定导入数据的sql语句，必填项，可以设置自己的提取逻辑，
            // 设置增量变量log_id，增量变量名称#[log_id]可以多次出现在sql语句的不同位置中，例如：
            // select * from td_sm_log where log_id > #[log_id] and parent_id = #[log_id]
            // 需要设置setLastValueColumn信息log_id，
            // 通过setLastValueType方法告诉工具增量字段的类型，默认是数字类型

//		importBuilder.setSql("select * from td_sm_log where log_id > #[log_id]");
//		importBuilder.addIgnoreFieldMapping("remark1");
//		importBuilder.setSql("select * from td_sm_log ");
            /**
             * 源db相关配置
             */
            DBInputConfig dbInputConfig = new DBInputConfig();
            dbInputConfig.setSql("select * from batchtest");
            //源数据源是从jobdatamap中传参进来的
            dbInputConfig.setDbName("second")
                    .setDbDriver("com.mysql.cj.jdbc.Driver")
                    .setDbUrl("jdbc:mysql://127.0.0.1:3306/insertsql?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC")
                    .setDbUser("root")
                    .setDbPassword("123456")
                    .setDbInitSize(10)
                    .setDbMaxSize(20)
                    .setDbMinIdleSize(20)
                    .setUsePool(true);
            importBuilder.setInputConfig(dbInputConfig);

            importBuilder
//                .setSqlFilepath("sql.xml")
//                .setSqlName("demoexport")
                    .setUseLowcase(false)  //可选项，true 列名称转小写，false列名称不转换小写，默认false，只要在UseJavaName为false的情况下，配置才起作用
                    .setPrintTaskLog(true); //可选项，true 打印任务执行日志（耗时，处理记录数） false 不打印，默认值false
            //项目中target数据源是配置是从application文件中加入的
            DBOutputConfig dbOutputConfig = new DBOutputConfig();
            dbOutputConfig.setDbName("target")
                    .setDbDriver("com.mysql.cj.jdbc.Driver") //数据库驱动程序，必须导入相关数据库的驱动jar包
                    .setDbUrl("jdbc:mysql://127.0.0.1:3306/qrtz?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC") //通过useCursorFetch=true启用mysql的游标fetch机制，否则会有严重的性能隐患，useCursorFetch必须和jdbcFetchSize参数配合使用，否则不会生效
                    .setDbUser("root")
                    .setDbPassword("123456")
                    .setValidateSQL("select 1")
                    .setDbInitSize(10)
                    .setDbMaxSize(20)
                    .setDbMinIdleSize(20)
                    .setUsePool(true)//是否使用连接池
                    .setInsertSql(insertsql); //可选项,批量导入db的记录数，默认为-1，逐条处理，> 0时批量处理

            importBuilder.setOutputConfig(dbOutputConfig);
            //定时任务配置，
            importBuilder.setFixedRate(false);//参考jdk timer task文档对fixedRate的说明
//					 .setScheduleDate(date) //指定任务开始执行时间：日期
//                .setDeyLay(1000L); // 任务延迟执行deylay毫秒后执行
//                .setPeriod(5000L); //每隔period毫秒执行，如果不设置，只执行一次
            //定时任务配置结束
//
            //设置任务执行拦截器，可以添加多个，定时任务每次执行的拦截器
            importBuilder.addCallInterceptor(new CallInterceptor() {
                @Override
                public void preCall(TaskContext taskContext) {
                    System.out.println("preCall");
                }

                @Override
                public void afterCall(TaskContext taskContext) {
                    System.out.println("afterCall");
                }

                @Override
                public void throwException(TaskContext taskContext, Throwable e) {
                    System.out.println("throwException");
                }

            }).addCallInterceptor(new CallInterceptor() {
                @Override
                public void preCall(TaskContext taskContext) {
                    System.out.println("preCall 1");
                }

                @Override
                public void afterCall(TaskContext taskContext) {
                    System.out.println("afterCall 1");
                }

                @Override
                public void throwException(TaskContext taskContext, Throwable e) {
                    System.out.println("throwException 1");
                }
            });
            importBuilder.setFromFirst(true);//setFromfirst(false)，如果作业停了，作业重启后从上次截止位置开始采集数据，
//        //setFromfirst(true) 如果作业停了，作业重启后，重新开始采集数据

            //映射和转换配置结束
            /**
             * 内置线程池配置，实现多线程并行数据导入功能，作业完成退出时自动关闭该线程池
             */
            importBuilder.setParallel(true);//设置为多线程并行批量导入,false串行
            importBuilder.setQueue(10);//设置批量导入线程池等待队列长度
            importBuilder.setThreadCount(50);//设置批量导入线程池工作线程数量
            importBuilder.setContinueOnError(true);//任务出现异常，是否继续执行作业：true（默认值）继续执行 false 中断作业执行



            importBuilder.setExportResultHandler(new ExportResultHandler<String>() {
                @Override
                public void success(TaskCommand<String> taskCommand, String result) {
                    TaskMetrics taskMetrics = taskCommand.getTaskMetrics();
                    logger.info(taskMetrics.toString());
                }

                @Override
                public void error(TaskCommand<String> taskCommand, String result) {
                    TaskMetrics taskMetrics = taskCommand.getTaskMetrics();
                    logger.info(taskMetrics.toString());
                }

                @Override
                public void exception(TaskCommand<String> taskCommand, Throwable exception) {
                    TaskMetrics taskMetrics = taskCommand.getTaskMetrics();
                    logger.info(taskMetrics.toString());
                }


            });
            return importBuilder;
        });



    }

}
