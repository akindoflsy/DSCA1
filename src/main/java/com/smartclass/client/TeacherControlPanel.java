package com.smartclass.client;

import com.smartclass.attendance.*;
import com.smartclass.environment.*;
import com.smartclass.smartboard.*;
import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

public class TeacherControlPanel extends JFrame {
    private JTextArea logArea;

    private AttendanceServiceGrpc.AttendanceServiceBlockingStub attendanceStub;
    private EnvironmentServiceGrpc.EnvironmentServiceBlockingStub environmentStub;
    private SmartBoardServiceGrpc.SmartBoardServiceBlockingStub smartBoardStub;

    private AttendanceServiceGrpc.AttendanceServiceStub attendanceAsyncStub;
    private EnvironmentServiceGrpc.EnvironmentServiceStub environmentAsyncStub;
    private SmartBoardServiceGrpc.SmartBoardServiceStub smartBoardAsyncStub;

    private static final String AUTH_TOKEN = "Bearer smartclass-secret-token";
    private static final String CLIENT_ID = "TeacherPanel-001";

    private volatile Context.CancellableContext attendanceStreamContext;
    private volatile Context.CancellableContext liveMetricsStreamContext;
    private volatile Context.CancellableContext liveSessionContext;

    public TeacherControlPanel() {
        setupGUI();
        discoverServices();
    }

    // Metadata
    private Metadata buildAuthMetadata() {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), AUTH_TOKEN);
        metadata.put(Metadata.Key.of("client-id", Metadata.ASCII_STRING_MARSHALLER), CLIENT_ID);
        return metadata;
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
        buttonPanel.setLayout(new GridLayout(4, 3));

        JButton btnAttendance = new JButton("Test Attendance");
        btnAttendance.addActionListener(e -> callAttendanceService());

        JButton btnEnvironment = new JButton("Get Metrics");
        btnEnvironment.addActionListener(e -> callEnvironmentService());

        JButton btnSmartBoard = new JButton("Push Content");
        btnSmartBoard.addActionListener(e -> callSmartBoardService());

        JButton btnStreamAttendance = new JButton("Stream Attendance");
        btnStreamAttendance.addActionListener(e -> streamAttendanceService());

        JButton btnStreamLiveMetrics = new JButton("Stream Live Metrics");
        btnStreamLiveMetrics.addActionListener(e -> streamLiveMetricsService());

        JButton btnUploadSensors = new JButton("Upload Sensors");
        btnUploadSensors.addActionListener(e -> uploadSensorBatchService());

        JButton btnLiveSession = new JButton("Live Session");
        btnLiveSession.addActionListener(e -> liveClassSessionService());

        // Cancel
        JButton btnCancelAttendance = new JButton("Cancel Attendance Stream");
        btnCancelAttendance.addActionListener(e -> cancelAttendanceStream());

        JButton btnCancelMetrics = new JButton("Cancel Metrics Stream");
        btnCancelMetrics.addActionListener(e -> cancelLiveMetricsStream());

        JButton btnCancelSession = new JButton("Cancel Live Session");
        btnCancelSession.addActionListener(e -> cancelLiveSession());

        buttonPanel.add(btnAttendance);
        buttonPanel.add(btnEnvironment);
        buttonPanel.add(btnSmartBoard);
        buttonPanel.add(btnStreamAttendance);
        buttonPanel.add(btnStreamLiveMetrics);
        buttonPanel.add(btnUploadSensors);
        buttonPanel.add(btnLiveSession);
        buttonPanel.add(btnCancelAttendance);
        buttonPanel.add(btnCancelMetrics);
        buttonPanel.add(btnCancelSession);

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

                    ClientInterceptor authInterceptor = MetadataUtils.newAttachHeadersInterceptor(buildAuthMetadata());

                    switch (serviceName) {
                        case "AttendanceService":
                            attendanceStub = AttendanceServiceGrpc.newBlockingStub(channel)
                                    .withInterceptors(authInterceptor);
                            attendanceAsyncStub = AttendanceServiceGrpc.newStub(channel)
                                    .withInterceptors(authInterceptor);
                            break;
                        case "EnvironmentService":
                            environmentStub = EnvironmentServiceGrpc.newBlockingStub(channel)
                                    .withInterceptors(authInterceptor);
                            environmentAsyncStub = EnvironmentServiceGrpc.newStub(channel)
                                    .withInterceptors(authInterceptor);
                            break;
                        case "SmartBoardService":
                            smartBoardStub = SmartBoardServiceGrpc.newBlockingStub(channel)
                                    .withInterceptors(authInterceptor);
                            smartBoardAsyncStub = SmartBoardServiceGrpc.newStub(channel)
                                    .withInterceptors(authInterceptor);
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
            // Deadline of 5 seconds
            AttendanceResponse response = attendanceStub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .logAttendance(request);
            logMessage("Attendance Server: " + response.getMessage());
        } catch (StatusRuntimeException e) {
            logMessage("RPC Failed:" + e.getStatus().getCode() + e.getStatus().getDescription());
        } catch (Exception e) {
            logMessage("Unexpected Error: " + e.getMessage());
        }
    }

    private void callEnvironmentService() {
        if (environmentStub == null) {
            logMessage("Error: EnvironmentService not connected.");
            return;
        }
        try {
            com.smartclass.environment.EmptyEnvironment request = com.smartclass.environment.EmptyEnvironment.newBuilder().build();
            MetricsResponse response = environmentStub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .getMetrics(request);
            logMessage("Environment Server: Noise=" + response.getNoiseLevel() + "dB, Lux=" + response.getLuxLevel());
        } catch (StatusRuntimeException e) {
            logMessage("RPC Failed:" + e.getStatus().getCode() + e.getStatus().getDescription());
        } catch (Exception e) {
            logMessage("Unexpected Error: " + e.getMessage());
        }
    }

    private void callSmartBoardService() {
        if (smartBoardStub == null) {
            logMessage("Error: SmartBoardService not connected.");
            return;
        }
        try {
            ContentRequest request = ContentRequest.newBuilder()
                    .setLessonUrl("https://moodle2025.ncirl.ie/mod/resource/view.php?id=45711")
                    .setMediaType("PDF")
                    .build();
            ActionResponse response = smartBoardStub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .pushContent(request);
            logMessage("SmartBoard Server: " + response.getStatusMessage());
        } catch (StatusRuntimeException e) {
            logMessage("RPC Failed: " + e.getStatus().getCode() + e.getStatus().getDescription());
        } catch (Exception e) {
            logMessage("Unexpected Error: " + e.getMessage());
        }
    }

    // Cancel
    private void streamAttendanceService() {
        if (attendanceAsyncStub == null) {
            logMessage("Error: AttendanceService not connected.");
            return;
        }
        attendanceStreamContext = Context.current().withCancellation();
        attendanceStreamContext.run(() -> {
            com.smartclass.attendance.EmptyAttendance request = com.smartclass.attendance.EmptyAttendance.newBuilder().build();
            // Deadline of 30 seconds
            attendanceAsyncStub.withDeadlineAfter(30, TimeUnit.SECONDS)
                    .streamAttendanceLogs(request, new StreamObserver<AttendanceRecord>() {
                @Override
                public void onNext(AttendanceRecord record) {
                    logMessage("Attendance Stream: " + record.getStudentId() + " - " + record.getStatus());
                }

                @Override
                public void onError(Throwable t) {
                    Status status = Status.fromThrowable(t);
                    if (status.getCode() == Status.Code.CANCELLED) {
                        logMessage("Attendance Stream: Cancelled by user.");
                    } else if (status.getCode() == Status.Code.DEADLINE_EXCEEDED) {
                        logMessage("Attendance Stream: Deadline exceeded.");
                    } else {
                        logMessage("Attendance Stream Error:" + status.getCode() + status.getDescription());
                    }
                }

                @Override
                public void onCompleted() {
                    logMessage("Attendance Stream Completed");
                }
            });
        });
    }

    private void cancelAttendanceStream() {
        if (attendanceStreamContext != null) {
            attendanceStreamContext.cancel(new Exception("User cancelled attendance stream"));
            logMessage("System: Attendance stream cancellation requested.");
        } else {
            logMessage("System: No active attendance stream to cancel.");
        }
    }

    private void streamLiveMetricsService() {
        if (environmentAsyncStub == null) {
            logMessage("Error: EnvironmentService is not connected.");
            return;
        }
        // Cancel
        liveMetricsStreamContext = Context.current().withCancellation();
        liveMetricsStreamContext.run(() -> {
            com.smartclass.environment.EmptyEnvironment request = com.smartclass.environment.EmptyEnvironment.newBuilder().build();
            environmentAsyncStub.withDeadlineAfter(30, TimeUnit.SECONDS)
                    .streamLiveMetrics(request, new StreamObserver<MetricsResponse>() {
                @Override
                public void onNext(MetricsResponse response) {
                    logMessage("Live Metrics: Noise=" + response.getNoiseLevel() + "dB, Lux=" + response.getLuxLevel());
                }

                @Override
                public void onError(Throwable t) {
                    Status status = Status.fromThrowable(t);
                    if (status.getCode() == Status.Code.CANCELLED) {
                        logMessage("Live Metrics Stream: Cancelled by user.");
                    } else if (status.getCode() == Status.Code.DEADLINE_EXCEEDED) {
                        logMessage("Live Metrics Stream: Deadline exceeded.");
                    } else {
                        logMessage("Live Metrics Stream Error:" + status.getCode() + status.getDescription());
                    }
                }

                @Override
                public void onCompleted() {
                    logMessage("Live Metrics Stream Completed.");
                }
            });
        });
    }

    private void cancelLiveMetricsStream() {
        if (liveMetricsStreamContext != null) {
            liveMetricsStreamContext.cancel(new Exception("User cancelled the live metrics stream."));
            logMessage("System: Live metrics stream requested cancellation.");
        } else {
            logMessage("System: No active live metrics stream can be cancelled.");
        }
    }

    private void uploadSensorBatchService() {
        if (environmentAsyncStub == null) {
            logMessage("Error: EnvironmentService is not connected.");
            return;
        }
        StreamObserver<BatchUploadResponse> responseObserver = new StreamObserver<BatchUploadResponse>() {
            @Override
            public void onNext(BatchUploadResponse response) {
                logMessage("Environment Upload: Success=" + response.getSuccess() + ", Received=" + response.getReadingsReceived());
            }

            @Override
            public void onError(Throwable t) {
                Status status = Status.fromThrowable(t);
                logMessage("Environment Upload Error:" + status.getCode() + status.getDescription());
            }

            @Override
            public void onCompleted() {
                logMessage("Environment Upload Completed.");
            }
        };

        StreamObserver<SensorReading> requestObserver = environmentAsyncStub
                .withDeadlineAfter(30, TimeUnit.SECONDS)
                .uploadSensorBatch(responseObserver);
        try {
            requestObserver.onNext(SensorReading.newBuilder().setSensorId("noise: ").setValue(45.5f).build());
            requestObserver.onNext(SensorReading.newBuilder().setSensorId("lux: ").setValue(300.0f).build());
            requestObserver.onCompleted();
        } catch (StatusRuntimeException e) {
            logMessage("RPC Failed:" + e.getStatus().getCode() + e.getStatus().getDescription());
        } catch (Exception e) {
            requestObserver.onError(e);
            logMessage("Unexpected Error: " + e.getMessage());
        }
    }

    private void liveClassSessionService() {
        if (smartBoardAsyncStub == null) {
            logMessage("Error: SmartBoardService not connected.");
            return;
        }
        liveSessionContext = Context.current().withCancellation();
        liveSessionContext.run(() -> {
            StreamObserver<BoardEvent> responseObserver = new StreamObserver<BoardEvent>() {
                @Override
                public void onNext(BoardEvent event) {
                    logMessage("SmartBoard Stream: " + event.getEventInfo());
                }

                @Override
                public void onError(Throwable t) {
                    Status status = Status.fromThrowable(t);
                    if (status.getCode() == Status.Code.CANCELLED) {
                        logMessage("SmartBoard Stream: Cancelled by user.");
                    } else if (status.getCode() == Status.Code.DEADLINE_EXCEEDED) {
                        logMessage("SmartBoard Stream: Deadline exceeded.");
                    } else {
                        logMessage("SmartBoard Stream Error:" + status.getCode() + status.getDescription());
                    }
                }

                @Override
                public void onCompleted() {
                    logMessage("SmartBoard stream is completed.");
                }
            };

            StreamObserver<TeacherCommand> requestObserver = smartBoardAsyncStub
                    .withDeadlineAfter(30, TimeUnit.SECONDS)
                    .liveClassSession(responseObserver);
            try {
                requestObserver.onNext(TeacherCommand.newBuilder().setCommand("NEXT_SLIDE").build());
                requestObserver.onNext(TeacherCommand.newBuilder().setCommand("LOCK_BOARD").build());
                requestObserver.onCompleted();
            } catch (StatusRuntimeException e) {
                logMessage("RPC Failed: " + e.getStatus().getCode() + e.getStatus().getDescription());
            } catch (Exception e) {
                requestObserver.onError(e);
                logMessage("Unexpected Error: " + e.getMessage());
            }
        });
    }

    private void cancelLiveSession() {
        if (liveSessionContext != null) {
            liveSessionContext.cancel(new Exception("User cancelled live session."));
            logMessage("System: Live session cancellation requested.");
        } else {
            logMessage("System: No active live session to cancel.");
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
