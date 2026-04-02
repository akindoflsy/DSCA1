package com.smartclass.server;

import com.smartclass.environment.*;
import io.grpc.Context;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class EnvironmentServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 50052;
        Server server = ServerBuilder.forPort(port)
                .addService(new EnvironmentServiceImpl())
                .intercept(new AuthInterceptor())
                .build()
                .start();

        System.out.println("Environment server started, listening on " + port);

        // JmDNS service publishing
        InetAddress jmdnsAddress = getSiteLocalAddress();
        System.out.println("JmDNS binding to: " + jmdnsAddress.getHostAddress());
        JmDNS jmdns = JmDNS.create(jmdnsAddress);
        ServiceInfo serviceInfo = ServiceInfo.create("_grpc._tcp.local.", "EnvironmentService", port, "path=index");
        jmdns.registerService(serviceInfo);
        System.out.println("Registered EnvironmentService with JmDNS");

        server.awaitTermination();
    }

    static InetAddress getSiteLocalAddress() throws IOException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            if (ni.isLoopback() || !ni.isUp()) continue;
            Enumeration<InetAddress> addresses = ni.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (addr instanceof java.net.Inet4Address && addr.isSiteLocalAddress()) {
                    return addr;
                }
            }
        }
        return InetAddress.getLocalHost();
    }

    static class EnvironmentServiceImpl extends EnvironmentServiceGrpc.EnvironmentServiceImplBase {

        // Simple RPC
        @Override
        public void getMetrics(EmptyEnvironment req, StreamObserver<MetricsResponse> responseObserver) {
            System.out.println("Received request for current metrics.");

            try {
                MetricsResponse response = MetricsResponse.newBuilder()
                        .setNoiseLevel(50)
                        .setLuxLevel(300)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } catch (Exception e) {
                responseObserver.onError(
                        Status.INTERNAL
                                .withDescription("Failed to retrieve metrics: " + e.getMessage())
                                .withCause(e)
                                .asRuntimeException());
            }
        }

        // Server-Side Streaming RPC
        @Override
        public void streamLiveMetrics(EmptyEnvironment req, StreamObserver<MetricsResponse> responseObserver) {
            System.out.println("Starting live metrics stream to client.");
            for (int i = 0; i < 5; i++) {
                // Check if the client has cancelled the stream.
                if (Context.current().isCancelled()) {
                    System.out.println("Client cancelled the live metrics stream.");
                    responseObserver.onError(
                            Status.CANCELLED
                                    .withDescription("Client cancelled the live metrics stream.")
                                    .asRuntimeException());
                    return;
                }

                MetricsResponse response = MetricsResponse.newBuilder()
                        .setNoiseLevel(40 + (int)(Math.random() * 20))
                        .setLuxLevel(300.0f + (float)(Math.random() * 50))
                        .build();
                responseObserver.onNext(response);
                try { Thread.sleep(2000); } catch (InterruptedException e) {
                    System.err.println("Stream interrupted: " + e.getMessage());
                    responseObserver.onError(
                            Status.ABORTED
                                    .withDescription("Metrics stream is interrupted on server.")
                                    .asRuntimeException());
                    return;
                }
            }
            responseObserver.onCompleted();
        }

        // Client-Side Streaming RPC
        @Override
        public StreamObserver<SensorReading> uploadSensorBatch(StreamObserver<BatchUploadResponse> responseObserver) {
            return new StreamObserver<SensorReading>() {
                int count = 0;

                @Override
                public void onNext(SensorReading reading) {
                    // Input validation
                    if (reading.getSensorId() == null || reading.getSensorId().isEmpty()) {
                        responseObserver.onError(Status.INVALID_ARGUMENT
                                .withDescription("Sensor ID can not be empty.")
                                .asRuntimeException());
                        return;
                    }

                    System.out.println("Received sensor data from: " + reading.getSensorId() + " Value: " + reading.getValue());
                    count++;
                }

                @Override
                public void onError(Throwable t) {
                    System.err.println("Error in receiving sensor batch: " + t.getMessage());
                }

                @Override
                public void onCompleted() {
                    System.out.println("Sensor batch upload completed. Total readings: " + count);
                    BatchUploadResponse response = BatchUploadResponse.newBuilder()
                            .setSuccess(true)
                            .setReadingsReceived(count)
                            .build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                }
            };
        }
    }
}