package com.smartclass.server;

import com.smartclass.attendance.*;
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

public class AttendanceServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 50051;
        Server server = ServerBuilder.forPort(port)
                .addService(new AttendanceServiceImpl())
                .intercept(new AuthInterceptor())
                .build()
                .start();

        System.out.println("Attendance Server started, listening on " + port);

        // JmDNS service publishing
        InetAddress jmdnsAddress = getSiteLocalAddress();
        System.out.println("JmDNS binding to: " + jmdnsAddress.getHostAddress());
        JmDNS jmdns = JmDNS.create(jmdnsAddress);
        ServiceInfo serviceInfo = ServiceInfo.create("_grpc._tcp.local.", "AttendanceService", port, "path=index");
        jmdns.registerService(serviceInfo);
        System.out.println("Registered AttendanceService with JmDNS");

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

    static class AttendanceServiceImpl extends AttendanceServiceGrpc.AttendanceServiceImplBase {
        @Override
        public void logAttendance(StudentRequest req, StreamObserver<AttendanceResponse> responseObserver) {
            // Validation
            if (req.getStudentId() == null || req.getStudentId().isEmpty()) {
                responseObserver.onError(
                        Status.INVALID_ARGUMENT
                                .withDescription("Student ID can not be empty.")
                                .asRuntimeException());
                return;
            }
            if (req.getTimestamp() == null || req.getTimestamp().isEmpty()) {
                responseObserver.onError(
                        Status.INVALID_ARGUMENT
                                .withDescription("timestamp can not be empty.")
                                .asRuntimeException());
                return;
            }

            System.out.println("Received scan for: " + req.getStudentId());
            AttendanceResponse response = AttendanceResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Student " + req.getStudentId() + " has been logged successfully.")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted(); // Simple RPC
        }

        @Override
        public void streamAttendanceLogs(EmptyAttendance req, StreamObserver<AttendanceRecord> responseObserver) {
            for (int i = 1; i < 6; i++) {
                // Check if the client has cancelled
                if (Context.current().isCancelled()) {
                    System.out.println("Client cancelled the attendance stream.");
                    responseObserver.onError(
                            Status.CANCELLED
                                    .withDescription("Client cancelled the attendance stream.")
                                    .asRuntimeException());
                    return;
                }

                AttendanceRecord record = AttendanceRecord.newBuilder()
                        .setStudentId("Student" + i)
                        .setTimestamp(System.currentTimeMillis() + "")
                        .setStatus("Present")
                        .build();
                responseObserver.onNext(record);
                try { Thread.sleep(1000); } catch (InterruptedException e) {
                    System.err.println("Stream is interrupted: " + e.getMessage());
                    responseObserver.onError(
                            Status.ABORTED
                                    .withDescription("Stream is interrupted on server.")
                                    .asRuntimeException());
                    return;
                }
            }
            responseObserver.onCompleted();
        }
    }
}