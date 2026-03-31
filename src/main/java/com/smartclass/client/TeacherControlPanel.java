package com.smartclass.client;

import com.smartclass.attendance.*;
import com.smartclass.environment.*;
import com.smartclass.smartboard.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.InetAddress;

public class TeacherControlPanel extends JFrame {
    private JTextArea logArea;

    private AttendanceServiceGrpc.AttendanceServiceBlockingStub attendanceStub;
    private EnvironmentServiceGrpc.EnvironmentServiceBlockingStub environmentStub;
    private SmartBoardServiceGrpc.SmartBoardServiceBlockingStub smartBoardStub;
    
    private AttendanceServiceGrpc.AttendanceServiceStub attendanceAsyncStub;
    private EnvironmentServiceGrpc.EnvironmentServiceStub environmentAsyncStub;
    private SmartBoardServiceGrpc.SmartBoardServiceStub smartBoardAsyncStub;

    public TeacherControlPanel() {
        setupGUI();
        discoverServices();
    }

    private void setupGUI() {
        setTitle("Teacher Control Panel");
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        logArea = new JTextArea();
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(2, 3));

        JButton btnAttendance = new JButton("Test Attendance");
        btnAttendance.addActionListener(e -> callAttendanceService());

        JButton btnEnvironment = new JButton("Get Metrics");
        btnEnvironment.addActionListener(e -> callEnvironmentService());

        JButton btnSmartBoard = new JButton("Push Content");
        btnSmartBoard.addActionListener(e -> callSmartBoardService());

        JButton btnStreamAttendance = new JButton("Stream Attendance");
        btnStreamAttendance.addActionListener(e -> streamAttendanceService());

        JButton btnUploadSensors = new JButton("Upload Sensors");
        btnUploadSensors.addActionListener(e -> uploadSensorBatchService());
        
        JButton btnLiveSession = new JButton("Live Session");
        btnLiveSession.addActionListener(e -> liveClassSessionService());

        buttonPanel.add(btnAttendance);
        buttonPanel.add(btnEnvironment);
        buttonPanel.add(btnSmartBoard);
        buttonPanel.add(btnStreamAttendance);
        buttonPanel.add(btnUploadSensors);
        buttonPanel.add(btnLiveSession);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void discoverServices() {
        logMessage("System: Starting JmDNS Service Discovery...");
        try {
            JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
            jmdns.addServiceListener("_grpc._tcp.local.", new ServiceListener() {
                @Override
                public void serviceAdded(ServiceEvent event) {
                    jmdns.requestServiceInfo(event.getType(), event.getName());
                }

                @Override
                public void serviceRemoved(ServiceEvent event) {
                    logMessage("System: Service disconnected - " + event.getName());
                }

                @Override
                public void serviceResolved(ServiceEvent event) {
                    String serviceName = event.getName();
                    String[] addresses = event.getInfo().getHostAddresses();

                    if (addresses == null || addresses.length == 0) {
                        logMessage("System: Address not resolved for " + serviceName);
                        return;
                    }

                    String host = addresses[0];
                    int port = event.getInfo().getPort();

                    logMessage("System: Discovered " + serviceName + " at " + host + ":" + port);

                    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                            .usePlaintext()
                            .build();

                    switch (serviceName) {
                        case "AttendanceService":
                            attendanceStub = AttendanceServiceGrpc.newBlockingStub(channel);
                            attendanceAsyncStub = AttendanceServiceGrpc.newStub(channel);
                            break;
                        case "EnvironmentService":
                            environmentStub = EnvironmentServiceGrpc.newBlockingStub(channel);
                            environmentAsyncStub = EnvironmentServiceGrpc.newStub(channel);
                            break;
                        case "SmartBoardService":
                            smartBoardStub = SmartBoardServiceGrpc.newBlockingStub(channel);
                            smartBoardAsyncStub = SmartBoardServiceGrpc.newStub(channel);
                            break;
                    }
                }
            });
        } catch (IOException e) {
            logMessage("Error: JmDNS initialization failed - " + e.getMessage());
        }
    }

    private void callAttendanceService() {
        if (attendanceStub == null) {
            logMessage("Error: AttendanceService not connected.");
            return;
        }
        try {
            StudentRequest request = StudentRequest.newBuilder()
                    .setStudentId("x25116584")
                    .setTimestamp(String.valueOf(System.currentTimeMillis()))
                    .build();
            AttendanceResponse response = attendanceStub.logAttendance(request);
            logMessage("Attendance Server: " + response.getMessage());
        } catch (Exception e) {
            logMessage("RPC Failed: " + e.getMessage());
        }
    }

    private void callEnvironmentService() {
        if (environmentStub == null) {
            logMessage("Error: EnvironmentService not connected.");
            return;
        }
        try {
            com.smartclass.environment.Empty request = com.smartclass.environment.Empty.newBuilder().build();
            MetricsResponse response = environmentStub.getMetrics(request);
            logMessage("Environment Server: Noise=" + response.getNoiseLevel() + "dB, Lux=" + response.getLuxLevel());
        } catch (Exception e) {
            logMessage("RPC Failed: " + e.getMessage());
        }
    }

    private void callSmartBoardService() {
        if (smartBoardStub == null) {
            logMessage("Error: SmartBoardService not connected.");
            return;
        }
        try {
            ContentRequest request = ContentRequest.newBuilder()
                    .setLessonUrl("http://local/lesson1.pdf")
                    .setMediaType("PDF")
                    .build();
            ActionResponse response = smartBoardStub.pushContent(request);
            logMessage("SmartBoard Server: " + response.getStatusMessage());
        } catch (Exception e) {
            logMessage("RPC Failed: " + e.getMessage());
        }
    }

    private void streamAttendanceService() {
        if (attendanceAsyncStub == null) {
            logMessage("Error: AttendanceService not connected.");
            return;
        }
        com.smartclass.attendance.Empty request = com.smartclass.attendance.Empty.newBuilder().build();
        attendanceAsyncStub.streamAttendanceLogs(request, new io.grpc.stub.StreamObserver<AttendanceRecord>() {
            @Override
            public void onNext(AttendanceRecord record) {
                logMessage("Attendance Stream: " + record.getStudentId() + " - " + record.getStatus());
            }

            @Override
            public void onError(Throwable t) {
                logMessage("Attendance Stream Error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                logMessage("Attendance Stream Completed");
            }
        });
    }

    private void uploadSensorBatchService() {
        if (environmentAsyncStub == null) {
            logMessage("Error: EnvironmentService not connected.");
            return;
        }
        io.grpc.stub.StreamObserver<BatchUploadResponse> responseObserver = new io.grpc.stub.StreamObserver<BatchUploadResponse>() {
            @Override
            public void onNext(BatchUploadResponse response) {
                logMessage("Environment Upload: Success=" + response.getSuccess() + ", Received=" + response.getReadingsReceived());
            }

            @Override
            public void onError(Throwable t) {
                logMessage("Environment Upload Error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                logMessage("Environment Upload Completed");
            }
        };

        io.grpc.stub.StreamObserver<SensorReading> requestObserver = environmentAsyncStub.uploadSensorBatch(responseObserver);
        try {
            requestObserver.onNext(SensorReading.newBuilder().setSensorId("noise-1").setValue(45.5f).build());
            requestObserver.onNext(SensorReading.newBuilder().setSensorId("lux-1").setValue(300.0f).build());
            requestObserver.onCompleted();
        } catch (Exception e) {
            requestObserver.onError(e);
            logMessage("RPC Failed: " + e.getMessage());
        }
    }

    private void liveClassSessionService() {
        if (smartBoardAsyncStub == null) {
            logMessage("Error: SmartBoardService not connected.");
            return;
        }
        io.grpc.stub.StreamObserver<BoardEvent> responseObserver = new io.grpc.stub.StreamObserver<BoardEvent>() {
            @Override
            public void onNext(BoardEvent event) {
                logMessage("SmartBoard Stream: " + event.getEventInfo());
            }

            @Override
            public void onError(Throwable t) {
                logMessage("SmartBoard Stream Error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                logMessage("SmartBoard Stream Completed");
            }
        };

        io.grpc.stub.StreamObserver<TeacherCommand> requestObserver = smartBoardAsyncStub.liveClassSession(responseObserver);
        try {
            requestObserver.onNext(TeacherCommand.newBuilder().setCommand("NEXT_SLIDE").build());
            requestObserver.onNext(TeacherCommand.newBuilder().setCommand("LOCK_BOARD").build());
            requestObserver.onCompleted();
        } catch (Exception e) {
            requestObserver.onError(e);
            logMessage("RPC Failed: " + e.getMessage());
        }
    }

    private void logMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TeacherControlPanel().setVisible(true));
    }
}
