FROM openjdk:8-slim

# Download Stackdriver Profiler Java agent
RUN apt-get -y update && apt-get install -qqy \
    wget \
    && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /opt/cprof && \
    wget -q -O- https://storage.googleapis.com/cloud-profiler/java/latest/profiler_java_agent.tar.gz \
    | tar xzv -C /opt/cprof && \
    rm -rf profiler_java_agent.tar.gz

RUN GRPC_HEALTH_PROBE_VERSION=v0.4.11 && \
    wget -qO/bin/grpc_health_probe https://github.com/grpc-ecosystem/grpc-health-probe/releases/download/${GRPC_HEALTH_PROBE_VERSION}/grpc_health_probe-linux-amd64 && \
    chmod +x /bin/grpc_health_probe

WORKDIR /app
#标志 --from=<name>
 #将从 from 指定的构建阶段中寻找源文件 <src>
COPY ./target/CurrencyService.jar /app/CurrencyService.jar

EXPOSE 7000
ENTRYPOINT java -jar /app/CurrencyService.jar \
 -Dlog4j2.contextDataInjector=io.opencensus.contrib.logcorrelation.log4j2.OpenCensusTraceContextDataInjector \
 -agentpath:/opt/cprof/profiler_java_agent.so=-cprof_service=currencyservice,-cprof_service_version=1.0.0