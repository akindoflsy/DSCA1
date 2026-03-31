package com.smartclass.server;

import com.smartclass.attendance.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;

public class AttendanceServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 50051;
        Server server = ServerBuilder.forPort(port)
                .addService(new AttendanceServiceImpl())
                .build()
                .start();

        System.out.println("Attendance Server started, listening on " + port);

        // JmDNS service publishing
        JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
        ServiceInfo serviceInfo = ServiceInfo.create("_grpc._tcp.local.", "AttendanceService", port, "path=index");
        jmdns.registerService(serviceInfo);
        System.out.println("Registered AttendanceService with JmDNS");

        server.awaitTermination();
    }

    static class AttendanceServiceImpl extends AttendanceServiceGrpc.AttendanceServiceImplBase {
        @Override
        public void logAttendance(StudentRequest req, StreamObserver<AttendanceResponse> responseObserver) {
            System.out.println("Received scan for: " + req.getStudentId());
            AttendanceResponse response = AttendanceResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Student " + req.getStudentId() + " logged successfully.")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted(); // Simple RPC
        }

        @Override
        public void streamAttendanceLogs(EmptyAttendance req, StreamObserver<AttendanceRecord> responseObserver) {
            for (int i = 0; i < 5; i++) {
                AttendanceRecord record = AttendanceRecord.newBuilder()
                        .setStudentId("Student-" + i)
                        .setTimestamp(System.currentTimeMillis() + "")
                        .setStatus("Present")
                        .build();
                responseObserver.onNext(record);
                try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }
            }
            responseObserver.onCompleted();
        }
    }
}