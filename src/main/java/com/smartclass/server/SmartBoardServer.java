package com.smartclass.server;

import com.smartclass.smartboard.*;
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

public class SmartBoardServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 50053;
        Server server = ServerBuilder.forPort(port)
                .addService(new SmartBoardServiceImpl())
                .intercept(new AuthInterceptor())
                .build()
                .start();

        System.out.println("SmartBoard Server started, listening on " + port);

        // JmDNS service publishing
        InetAddress jmdnsAddress = getSiteLocalAddress();
        System.out.println("JmDNS binding to: " + jmdnsAddress.getHostAddress());
        JmDNS jmdns = JmDNS.create(jmdnsAddress);
        ServiceInfo serviceInfo = ServiceInfo.create("_grpc._tcp.local.", "SmartBoardService", port, "path=index");
        jmdns.registerService(serviceInfo);
        System.out.println("Registered SmartBoardService with JmDNS.");

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

    static class SmartBoardServiceImpl extends SmartBoardServiceGrpc.SmartBoardServiceImplBase {

        // Simple RPC
        @Override
        public void pushContent(ContentRequest req, StreamObserver<ActionResponse> responseObserver) {
            // Input validation
            if (req.getLessonUrl() == null || req.getLessonUrl().isEmpty()) {
                responseObserver.onError(
                        Status.INVALID_ARGUMENT
                                .withDescription("Lesson URL can not be empty.")
                                .asRuntimeException());
                return;
            }
            if (req.getMediaType() == null || req.getMediaType().isEmpty()) {
                responseObserver.onError(
                        Status.INVALID_ARGUMENT
                                .withDescription("Media type can not be empty.")
                                .asRuntimeException());
                return;
            }

            System.out.println("Pushing content: " + req.getLessonUrl() + req.getMediaType());
            ActionResponse response = ActionResponse.newBuilder()
                    .setSuccess(true)
                    .setStatusMessage("Content successfully loaded on SmartBoard.")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        // Bidirectional Streaming RPC
        @Override
        public StreamObserver<TeacherCommand> liveClassSession(StreamObserver<BoardEvent> responseObserver) {
            return new StreamObserver<TeacherCommand>() {
                @Override
                public void onNext(TeacherCommand command) {
                    // Check if is cancelled
                    if (Context.current().isCancelled()) {
                        System.out.println("Client cancelled the live class session.");
                        responseObserver.onError(
                                Status.CANCELLED
                                        .withDescription("Client cancelled the live class session")
                                        .asRuntimeException());
                        return;
                    }

                    // Input validation
                    if (command.getCommand() == null || command.getCommand().isEmpty()) {
                        responseObserver.onError(
                                Status.INVALID_ARGUMENT
                                        .withDescription("Command must not be empty")
                                        .asRuntimeException());
                        return;
                    }

                    System.out.println("Received command from Teacher: " + command.getCommand());

                    String feedback;
                    if (command.getCommand().equals("NEXT_SLIDE")) {
                        feedback = "SLIDE_CHANGED_TO_NEXT";
                    } else if (command.getCommand().equals("LOCK_BOARD")) {
                        feedback = "BOARD_LOCKED_SUCCESSFULLY";
                    } else {
                        feedback = "COMMAND_EXECUTED: " + command.getCommand();
                    }

                    BoardEvent event = BoardEvent.newBuilder()
                            .setEventInfo(feedback)
                            .build();
                    responseObserver.onNext(event);
                }

                @Override
                public void onError(Throwable t) {
                    System.err.println("Session error: " + Status.fromThrowable(t).getCode() + t.getMessage());
                }

                @Override
                public void onCompleted() {
                    System.out.println("Teacher ended the live session.");
                    responseObserver.onCompleted();
                }
            };
        }
    }
}