package com.akhil.springbootconnect.grpc

import io.grpc.BindableService
import io.grpc.Server
import io.grpc.netty.NettyServerBuilder
import io.grpc.protobuf.services.ProtoReflectionServiceV1
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class GrpcServerRunner(
    private val services: List<BindableService>,
    @Value("\${grpc.server.port:9091}") private val port: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var server: Server? = null

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        val builder = NettyServerBuilder.forPort(port)
        services.forEach { builder.addService(it) }
        builder.addService(ProtoReflectionServiceV1.newInstance())
        server = builder.build().start()
        log.info("gRPC server started on port {}", server?.port)
    }

    @PreDestroy
    fun stop() {
        server?.shutdown()
        log.info("gRPC server stopped")
    }
}
