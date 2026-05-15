package com.akhil.springbootconnect.grpc

import io.grpc.BindableService
import jakarta.annotation.PreDestroy
import me.ivovk.connect_rpc_java.netty.ConnectNettyServerBuilder
import me.ivovk.connect_rpc_java.netty.server.NettyServer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ConnectServerRunner(
    private val services: List<BindableService>,
    @Value("\${connect.server.port:9090}") private val port: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var server: NettyServer? = null

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        server = ConnectNettyServerBuilder
            .forServices(*services.toTypedArray())
            .port(port)
            .build()
        log.info("Connect RPC server started on port {}", server?.port)
    }

    @PreDestroy
    fun stop() {
        server?.shutdown()
        log.info("Connect RPC server stopped")
    }
}
