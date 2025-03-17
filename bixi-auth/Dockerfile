FROM docker.1ms.run/library/eclipse-temurin:17-jre-ubi9-minimal

WORKDIR /bixi-auth

ARG JAR_FILE=target/bixi-auth.jar

COPY ${JAR_FILE} app.jar

EXPOSE 3000

ENV TZ=Asia/Shanghai JAVA_OPTS="-Xms256m -Xmx256m -Djava.security.egd=file:/dev/./urandom"

CMD sleep 60; java $JAVA_OPTS -jar app.jar