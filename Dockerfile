FROM crpi-zs2zecm2bgpvuse2.cn-hangzhou.personal.cr.aliyuncs.com/public-builder/liberica-openjdk-debian:25 AS builder

WORKDIR /app
ARG JAR_FILE=target/app.jar
COPY ${JAR_FILE} app.jar

RUN java -Djarmode=layertools -jar app.jar extract

################################

FROM crpi-zs2zecm2bgpvuse2.cn-hangzhou.personal.cr.aliyuncs.com/public-builder/java25-debian13:nonroot
WORKDIR /app
ARG PORT=8080
COPY --from=builder app/dependencies/ ./
COPY --from=builder app/spring-boot-loader/ ./
COPY --from=builder app/snapshot-dependencies/ ./
COPY --from=builder app/application/ ./

ENV TZ=Asia/Shanghai
ENV JAVA_TOOL_OPTIONS="-Djava.security.egd=file:/dev/./urandom"

EXPOSE ${PORT}

ENTRYPOINT ["java","org.springframework.boot.loader.launch.JarLauncher"]
