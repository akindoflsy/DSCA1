package com.smartclass.server;

import com.smartclass.smartboard.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;

public class SmartBoardServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 50053;
        Server server = ServerBuilder.forPort(port)
                .addService(new SmartBoardServiceImpl())
                .build()
                .start();

        System.out.println("SmartBoard Server started, listening on " + port);

        // JmDNS service publishing
        JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
        ServiceInfo serviceInfo = ServiceInfo.create("_grpc._tcp.local.", "SmartBoardService", port, "path=index");
        jmdns.registerService(serviceInfo);
        System.out.println("Registered SmartBoardService with JmDNS");

        server.awaitTermination();
    }

    static class SmartBoardServiceImpl extends SmartBoardServiceGrpc.SmartBoardServiceImplBase {

        // Simple RPC
        @Override
        public void pushContent(ContentRequest req, StreamObserver<ActionResponse> responseObserver) {
            System.out.println("Pushing content: " + req.getLessonUrl() + " [" + req.getMediaType() + "]");
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
            // Observer
            return new StreamObserver<TeacherCommand>() {
                @Override
                public void onNext(TeacherCommand command) {
                    System.out.println("Received command from Teacher: " + command.getCommand());

                    // responseObserver
                    String feedback = "";
                    if (command.getCommand().equals("NEXT_SLIDE")) {
                        feedback = "SLIDE_CHANGED_TO_NEXT";
                    } else if (command.getCommand().equals("LOCK_SCREEN")) {
                        feedback = "SCREEN_LOCKED_SUCCESSFULLY";
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
                    System.err.println("Session error: " + t.getMessage());
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