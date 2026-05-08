FROM alpine:3 AS extract
WORKDIR /build
COPY ./rudder-dist/target/rudder-*-bin.tar.gz .
RUN tar -zxf rudder-*-bin.tar.gz --strip-components=1 && rm rudder-*-bin.tar.gz

FROM eclipse-temurin:21-jre

ENV DOCKER=true
ENV TZ=Asia/Shanghai
ENV RUDDER_HOME=/opt/rudder

WORKDIR $RUDDER_HOME

COPY --from=extract /build/ $RUDDER_HOME/

RUN chmod +x bin/*.sh api-server/bin/*.sh

EXPOSE 5680 5690

CMD ["/bin/bash", "/opt/rudder/api-server/bin/start.sh"]
