package com.smartclass.server;

import com.smartclass.environment.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;

public class EnvironmentServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 50052;
        Server server = ServerBuilder.forPort(port)
                .addService(new EnvironmentServiceImpl())
                .build()
                .start();

        System.out.println("Environment Server started, listening on " + port);

        // JmDNS service publishing
        JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
        ServiceInfo serviceInfo = ServiceInfo.create("_grpc._tcp.local.", "EnvironmentService", port, "path=index");
        jmdns.registerService(serviceInfo);
        System.out.println("Registered EnvironmentService with JmDNS");

        server.awaitTermination();
    }

    static class EnvironmentServiceImpl extends EnvironmentServiceGrpc.EnvironmentServiceImplBase {

        // Simple RPC
        @Override
        public void getMetrics(EmptyEnvironment req, StreamObserver<MetricsResponse> responseObserver) {
            System.out.println("Received request for current metrics.");
            MetricsResponse response = MetricsResponse.newBuilder()
                    .setNoiseLevel(50) // mock data
                    .setLuxLevel(30) // mock data
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        // Server-Side Streaming RPC
        @Override
        public void streamLiveMetrics(EmptyEnvironment req, StreamObserver<MetricsResponse> responseObserver) {
            System.out.println("Starting live metrics stream to client...");
            for (int i = 0; i < 5; i++) {
                MetricsResponse response = MetricsResponse.newBuilder()
                        .setNoiseLevel(40 + (int)(Math.random() * 20)) // mock data
                        .setLuxLevel(300.0f + (float)(Math.random() * 50))
                        .build();
                responseObserver.onNext(response);
                try { Thread.sleep(2000); } catch (InterruptedException e) { e.printStackTrace(); }
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