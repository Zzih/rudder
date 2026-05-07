FROM eclipse-temurin:21-jre

ENV DOCKER=true
ENV TZ=Asia/Shanghai
ENV RUDDER_HOME=/opt/rudder

WORKDIR $RUDDER_HOME

COPY ./rudder-dist/target/rudder-*-SNAPSHOT.tar.gz $RUDDER_HOME
RUN tar -zxf rudder-*-SNAPSHOT.tar.gz && \
    rm -f rudder-*-SNAPSHOT.tar.gz && \
    chmod +x bin/*.sh api-server/bin/*.sh

EXPOSE 5680 5690

CMD ["/bin/bash", "/opt/rudder/api-server/bin/start.sh"]
