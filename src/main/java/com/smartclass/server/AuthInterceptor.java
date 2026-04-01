package com.smartclass.server;

import io.grpc.*;

public class AuthInterceptor implements ServerInterceptor {

    private static final String VALID_TOKEN = "Bearer smartclass-secret-token";

    public static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> CLIENT_ID_KEY =
            Metadata.Key.of("client-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        // Read and log the client-id metadata
        String clientId = headers.get(CLIENT_ID_KEY);
        if (clientId != null) {
            System.out.println("[Auth] Request from client-id: " + clientId);
        }

        String token = headers.get(AUTHORIZATION_KEY);

        if (token == null) {
            call.close(Status.UNAUTHENTICATED.withDescription("Authorization token is missing"), new Metadata());
            return new ServerCall.Listener<ReqT>() {};
        }

        if (!VALID_TOKEN.equals(token)) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid authorization token"), new Metadata());
            return new ServerCall.Listener<ReqT>() {};
        }

        // Proceed with the call
        return next.startCall(call, headers);
    }
}

