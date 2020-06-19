package io.grpc;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class ServerInterruptImpl  implements ServerInterceptor{
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        System.out.println("Execute server interceptor 1 and obtain token");
        // Obtain server attributes
        final Metadata.Key<String> token = Metadata.Key.of("token", Metadata.ASCII_STRING_MARSHALLER);
        final String tokenStr = headers.get(token);
        if (tokenStr.length() == 0){
            System.out.println("Error: unable to get token, connection closed.");
            call.close(Status.DATA_LOSS,headers);
        }
        // Add attributes back to headers
        ServerCall<ReqT, RespT> serverCall = new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
            @Override
            public void sendHeaders(Metadata headers) {
                System.out.println("Execute server interceptor 2 and add token.");
                headers.put(token,tokenStr);
                super.sendHeaders(headers);
            }
        };
        return next.startCall(serverCall,headers);
    }
}