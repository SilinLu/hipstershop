//https://blog.csdn.net/weixin_43887447/article/details/109605564参考
package hipstershop;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.Message;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.services.HealthStatusManager;
import io.opencensus.common.Duration;
import io.opencensus.contrib.grpc.metrics.RpcViews;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsConfiguration;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.exporter.trace.jaeger.JaegerExporterConfiguration;
import io.opencensus.exporter.trace.jaeger.JaegerTraceExporter;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceConfiguration;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceExporter;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class CurrencyService {
    private static final Logger logger = LogManager.getLogger(CurrencyService.class);
    private static final Tracer tracer = Tracing.getTracer();

    private Server server;
    private HealthStatusManager healthMgr;

    private static final CurrencyService service = new CurrencyService();

    private void start() throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7000"));
        healthMgr = new HealthStatusManager();

        server =
                ServerBuilder.forPort(port)
                        .addService(new CurrencyServiceImpl())
                        .addService(healthMgr.getHealthService())
                        .build()
                        .start();
        System.out.println("Currency Service started, listening on " + port);
        logger.info("Currency Service started, listening on " + port);
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                                    System.err.println(
                                            "*** shutting down gRPC ads server since JVM is shutting down");
                                    CurrencyService.this.stop();
                                    System.err.println("*** server shut down");
                                }));
        healthMgr.setStatus("", ServingStatus.SERVING);
    }

    private void stop() {
        if (server != null) {
            healthMgr.clearStatus("");
            server.shutdown();
            logger.info("CurrencyService Shutdown.");

        }
    }

    private static class CurrencyServiceImpl extends hipstershop.CurrencyServiceGrpc.CurrencyServiceImplBase {/**
     * Retrieves ads based on context provided in the request {@code CurrencyRequest}.
     *
     *
     * @param responseObserver the stream observer which gets notified with the value of {@code
     *     CurrencyResponse}
     */
        @Override
        public void convert(hipstershop.Demo.CurrencyConversionRequest request,
                            io.grpc.stub.StreamObserver<hipstershop.Demo.Money> responseObserver) {
            hipstershop.Demo.Money from=request.getFrom();
            Span span = tracer.getCurrentSpan();
            double nanos=from.getNanos();
            double units=from.getUnits();
            String to_code=request.getToCode();
            String currency_code= from.getCurrencyCode();
            try {
                JSONObject data =getCurrencies();
                span.putAttribute("method", AttributeValue.stringAttributeValue("convert"));
                //todo 转换过程获得money
                double euros_units = units / data.getDouble(currency_code);
                double euros_nanos = nanos / data.getDouble(currency_code);
                double []arr= carry(euros_units,euros_nanos);
                euros_nanos=arr[1];euros_units=arr[0];
                euros_nanos = Math.round(euros_nanos);
                double result_units=euros_units * data.getDouble(to_code);
                double result_nanos=euros_nanos * data.getDouble(to_code);
                arr=carry(result_units,result_nanos);
                result_nanos=arr[1];result_units=arr[0];
                result_units = Math.floor(result_units);
                result_nanos = Math.floor(result_nanos);
                currency_code = to_code;
                hipstershop.Demo.Money reply = Demo.Money.newBuilder().setUnits((long) result_units).setNanos((int) result_nanos).setCurrencyCode(currency_code).build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                logger.info("conversion request successful");
                logger.info(reply);
            } catch (StatusRuntimeException | IOException e) {
                logger.log(Level.WARN, "Convert Failed with status {}", e.toString());
                responseObserver.onError(e);
            }
        }
        private double[] carry(double units,double nanos){
            double fractionSize = Math.pow(10, 9);
            nanos += (units % 1) * fractionSize;
            units = Math.floor(units) + Math.floor(nanos / fractionSize);
            nanos = nanos % fractionSize;
            return new double[]{units, nanos};
        }


        private JSONObject getCurrencies() throws IOException {
            InputStream in = this.getClass().getClassLoader().getResourceAsStream("currency_conversion.json");

            //todo 获取json文件√
            String json = IOUtils.toString(in, Charset.forName("UTF-8"));
            JSONObject jsonObject= JSON.parseObject(json);

            return jsonObject;
        }

        @Override
        public void getSupportedCurrencies(hipstershop.Demo.Empty request,
                                           io.grpc.stub.StreamObserver<hipstershop.Demo.GetSupportedCurrenciesResponse> responseObserver) {

            Span span = tracer.getCurrentSpan();
            try {
                span.putAttribute("method", AttributeValue.stringAttributeValue("getSupportedCurrencies"));
                List<String> code =getCurrencies().keySet().stream().collect(Collectors.toList());
                hipstershop.Demo.GetSupportedCurrenciesResponse reply = hipstershop.Demo.GetSupportedCurrenciesResponse.newBuilder().addAllCurrencyCodes(code).build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                logger.info("getSupportedCurrencies request successful");
                logger.info(reply);
            } catch (StatusRuntimeException | IOException e) {
                logger.log(Level.WARN, "GetSupportedCurrencies Failed with status {}", e.toString());
                responseObserver.onError(e);
            }
        }

    }

    static CurrencyService getInstance() {
        return service;
    }

    /** Await termination on the main thread since the grpc library uses daemon threads. */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    private static void initStats() {
        if (System.getenv("DISABLE_STATS") != null) {
            logger.info("Stats disabled.");
            return;
        }
        logger.info("Stats enabled");

        long sleepTime = 10; /* seconds */
        int maxAttempts = 5;
        boolean statsExporterRegistered = false;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                if (!statsExporterRegistered) {
                    StackdriverStatsExporter.createAndRegister(
                            StackdriverStatsConfiguration.builder()
                                    .setExportInterval(Duration.create(60, 0))
                                    .build());
                    statsExporterRegistered = true;
                }
            } catch (Exception e) {
                if (i == (maxAttempts - 1)) {
                    logger.log(
                            Level.WARN,
                            "Failed to register Stackdriver Exporter."
                                    + " Stats data will not reported to Stackdriver. Error message: "
                                    + e.toString());
                } else {
                    logger.info("Attempt to register Stackdriver Exporter in " + sleepTime + " seconds ");
                    try {
                        Thread.sleep(TimeUnit.SECONDS.toMillis(sleepTime));
                    } catch (Exception se) {
                        logger.log(Level.WARN, "Exception while sleeping" + se.toString());
                    }
                }
            }
        }
        logger.info("Stats enabled - Stackdriver Exporter initialized.");
    }

    private static void initTracing() {
        if (System.getenv("DISABLE_TRACING") != null) {
            logger.info("Tracing disabled.");
            return;
        }
        logger.info("Tracing enabled");

        long sleepTime = 10; /* seconds */
        int maxAttempts = 5;
        boolean traceExporterRegistered = false;

        for (int i = 0; i < maxAttempts; i++) {
            try {
                if (!traceExporterRegistered) {
                    StackdriverTraceExporter.createAndRegister(
                            StackdriverTraceConfiguration.builder().build());
                    traceExporterRegistered = true;
                }
            } catch (Exception e) {
                if (i == (maxAttempts - 1)) {
                    logger.log(
                            Level.WARN,
                            "Failed to register Stackdriver Exporter."
                                    + " Tracing data will not reported to Stackdriver. Error message: "
                                    + e.toString());
                } else {
                    logger.info("Attempt to register Stackdriver Exporter in " + sleepTime + " seconds ");
                    try {
                        Thread.sleep(TimeUnit.SECONDS.toMillis(sleepTime));
                    } catch (Exception se) {
                        logger.log(Level.WARN, "Exception while sleeping" + se.toString());
                    }
                }
            }
        }
        logger.info("Tracing enabled - Stackdriver exporter initialized.");
    }




    private static void initJaeger() {
        String jaegerCurrencydr = System.getenv("JAEGER_SERVICE_ADDR");
        if (jaegerCurrencydr != null && !jaegerCurrencydr.isEmpty()) {
            String jaegerUrl = String.format("http://%s/api/traces", jaegerCurrencydr);
            // Register Jaeger Tracing.
            JaegerTraceExporter.createAndRegister(
                    JaegerExporterConfiguration.builder()
                            .setThriftEndpoint(jaegerUrl)
                            .setServiceName("currencyservice")
                            .build());
            logger.info("Jaeger initialization complete.");
        } else {
            logger.info("Jaeger initialization disabled.");
        }
    }

    /** Main launches the server from the command line. */
    public void startService() throws IOException, InterruptedException {

        // Registers all RPC views.
        RpcViews.registerAllGrpcViews();
        new Thread(
                () -> {
                    initStats();
                    initTracing();
                })
                .start();

        // Register Jaeger
        initJaeger();

        // Start the RPC server. You shouldn't see any output from gRPC before this.
        logger.info("CurrencyService starting.");
        final CurrencyService service = CurrencyService.getInstance();
        service.start();
        service.blockUntilShutdown();
        logger.info("CurrencyService Shutdown.");
    }

}
