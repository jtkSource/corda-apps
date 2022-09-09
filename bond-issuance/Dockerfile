FROM gradle:5.6.4-jdk8 as builder
WORKDIR /project
ADD cordapp-client/src /project/cordapp-client/src
ADD cordapp-client/build.gradle /project/cordapp-client
ADD contracts/src /project/contracts/src
ADD contracts/build.gradle /project/contracts/build.gradle
ADD workflows/src /project/workflows/src
ADD workflows/build.gradle /project/workflows/build.gradle
ADD build.gradle /project
ADD gradle.properties /project
ADD settings.gradle /project
ADD constants.properties /project
RUN gradle build shadowJar -x test --no-daemon

FROM adoptopenjdk:8-jre-hotspot
ENV APP_DIR /app
ENV APP_JAR cordapp-client.jar
ENV PORT 9092
ENV CONFIG_DIR $APP_DIR/config/dev
ENV NODE cb
ADD config $APP_DIR/config
EXPOSE $PORT
WORKDIR $APP_DIR
COPY --from=builder /project/cordapp-client/build/libs/cordapp-client-*-all.jar $APP_DIR/$APP_JAR
ENTRYPOINT ["sh","-c"]
CMD ["exec java -jar $APP_JAR $CONFIG_DIR $NODE"]

# docker build -t potholedfern/croda-client:2.2 .
# docker run -d -e NODE=gs -e PORT=9091 --name gs-node-server --network host potholedfern/croda-client:2.2
# docker run -d -e NODE=hsbc -e PORT=9092 --name hsbc-node-server --network host potholedfern/croda-client:2.2
# docker run -d -e NODE=citi -e PORT=9095 --name citi-node-server --network host potholedfern/croda-client:2.2
# docker run -d -e NODE=cb -e PORT=9096 --name cb-node-server --network host potholedfern/croda-client:2.2
# docker run -d -e NODE=mas -e PORT=9093 --name mas-node-server --network host potholedfern/croda-client:2.2
# docker run -d -e NODE=sgx -e PORT=9094 --name sgx-node-server --network host potholedfern/croda-client:2.2
# docker push potholedfern/croda-client:2.2
