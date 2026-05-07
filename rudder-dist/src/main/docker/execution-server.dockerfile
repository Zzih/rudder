FROM eclipse-temurin:21-jre

ENV DOCKER=true
ENV TZ=Asia/Shanghai
ENV RUDDER_HOME=/opt/rudder

WORKDIR $RUDDER_HOME

COPY ./rudder-dist/target/rudder-*-SNAPSHOT.tar.gz $RUDDER_HOME
RUN tar -zxf rudder-*-SNAPSHOT.tar.gz && \
    rm -f rudder-*-SNAPSHOT.tar.gz && \
    chmod +x bin/*.sh execution-server/bin/*.sh && \
    mkdir -p logs/tasks

EXPOSE 5681 5691

CMD ["/bin/bash", "/opt/rudder/execution-server/bin/start.sh"]
