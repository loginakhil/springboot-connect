package com.akhil.springbootconnect.grpc

import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Component

@Component
class GreeterService : GreeterGrpc.GreeterImplBase() {
    override fun sayHello(request: HelloRequest, responseObserver: StreamObserver<HelloReply>) {
        val reply = HelloReply.newBuilder()
            .setMessage("Hello, ${request.name}!")
            .build()
        responseObserver.onNext(reply)
        responseObserver.onCompleted()
    }
}
