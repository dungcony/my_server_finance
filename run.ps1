mvn clean
mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Xmx2g -Xlog:gc:file=gc.log:time,uptime"