FROM alpine/java:17.0.12-jre

WORKDIR /bixi-auth

ARG JAR_FILE=target/bixi-auth.jar

COPY ${JAR_FILE} app.jar

COPY ${JAR_FILE} app-1.jar

RUN rm -rf  app-1.jar

EXPOSE 3000

ENV TZ=Asia/Shanghai JAVA_OPTS="-Xms256m -Xmx256m -Djava.security.egd=file:/dev/./urandom"

CMD sleep 60; java $JAVA_OPTS -jar app.jar