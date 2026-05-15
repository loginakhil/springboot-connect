# springboot-connect

Spring Boot 4 (WebFlux + Kotlin) service exposing a gRPC `BindableService` over
**both** the [Connect protocol](https://connectrpc.com) (HTTP+JSON) and the
**traditional gRPC protocol** (HTTP/2 binary) — same service implementation,
two transports, two ports.

- Connect server: [`igor-vovk/connect-rpc-java`](https://github.com/igor-vovk/connect-rpc-java) Netty server
- gRPC server: stock `io.grpc:grpc-netty` with reflection enabled (`grpcurl`-friendly)

The single `GreeterService` (`BindableService`) bean is registered with both runners,
so identical behavior is exposed over both protocols.

## Ports

| Port | Protocol | Server | Configured by |
|------|----------|--------|---------------|
| `8080` | HTTP (WebFlux) | Spring Boot | `server.port` |
| `9090` | Connect (HTTP+JSON / +proto) | `ConnectServerRunner` | `connect.server.port` |
| `9091` | gRPC (HTTP/2 binary) | `GrpcServerRunner` | `grpc.server.port` |

> **Why two ports?** The Java Connect library only speaks the Connect protocol,
> and `grpc-java`'s Netty server only speaks gRPC framing. Neither multiplexes
> the other on the same port (unlike Connect-Go). Front them with Envoy if a
> single external port is required.

## Greeter service

Proto definition: [`src/main/proto/helloworld.proto`](src/main/proto/helloworld.proto).

```proto
service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply) {}
}
```

### 1. Start the server

```bash
./gradlew bootRun
```

Look for the log lines:

```
Connect RPC server started on port 9090
gRPC server started on port 9091
```

### 2. Call the service

The Connect protocol uses plain HTTP+JSON over `POST /<package>.<Service>/<Method>`,
so any HTTP client works.

#### `curl` (unary, JSON)

```bash
curl -X POST \
  -H 'Content-Type: application/json' \
  -d '{"name": "world"}' \
  http://localhost:9090/helloworld.Greeter/SayHello
```

Expected response:

```json
{"message":"Hello, world!"}
```

#### `curl` with verbose headers

Inspect Connect response headers (status, content-type, trailers as headers):

```bash
curl -i -X POST \
  -H 'Content-Type: application/json' \
  -H 'Connect-Protocol-Version: 1' \
  -d '{"name": "world"}' \
  http://localhost:9090/helloworld.Greeter/SayHello
```

#### `curl` error response

Empty / invalid request body — Connect returns a JSON error envelope:

```bash
curl -X POST \
  -H 'Content-Type: application/json' \
  -d '{}' \
  http://localhost:9090/helloworld.Greeter/SayHello
```

#### `curl` (unary, protobuf binary)

Pipe a binary-encoded `HelloRequest` (e.g. from `protoc --encode`) through curl:

```bash
echo 'name: "world"' \
  | protoc --encode=helloworld.HelloRequest src/main/proto/helloworld.proto \
  | curl -X POST \
      -H 'Content-Type: application/proto' \
      --data-binary @- \
      http://localhost:9090/helloworld.Greeter/SayHello \
  | protoc --decode=helloworld.HelloReply src/main/proto/helloworld.proto
```

#### Connect RPC clients

Generated Connect clients (e.g. via `buf` / `protoc-gen-connect-*`) point at
`http://localhost:9090` and call `Greeter.SayHello` directly.

### 2b. Call the service over traditional gRPC (port `9091`)

The gRPC server has [server reflection](https://github.com/grpc/grpc/blob/master/doc/server-reflection.md)
enabled, so `grpcurl` works without needing the `.proto` file.

#### List services / methods

```bash
grpcurl -plaintext localhost:9091 list
grpcurl -plaintext localhost:9091 list helloworld.Greeter
grpcurl -plaintext localhost:9091 describe helloworld.Greeter.SayHello
```

#### Unary call

```bash
grpcurl -plaintext \
  -d '{"name": "world"}' \
  localhost:9091 helloworld.Greeter/SayHello
```

Expected response:

```json
{
  "message": "Hello, world!"
}
```

#### Without reflection (using local proto)

```bash
grpcurl -plaintext \
  -import-path src/main/proto \
  -proto helloworld.proto \
  -d '{"name": "world"}' \
  localhost:9091 helloworld.Greeter/SayHello
```

#### Generated gRPC clients

Standard `protoc-gen-grpc-*` clients (Java/Kotlin/Go/Python/etc.) point at
`localhost:9091` and call `Greeter/SayHello` over HTTP/2.

### 3. Stop the server

`Ctrl+C` in the `bootRun` terminal. The `@PreDestroy` hooks in
`ConnectServerRunner` and `GrpcServerRunner` shut both Netty servers down.

## Build

```bash
./gradlew build
```

Proto sources are generated into `build/generated/source/proto/main/{java,grpc}`
by the `com.google.protobuf` Gradle plugin during the `generateProto` task.

## Run with Docker Compose

A multi-stage `Dockerfile` builds the `bootJar` with Gradle and runs it on
`eclipse-temurin:17-jre`. `docker-compose.yml` exposes all three ports
(`8080`, `9090`, `9091`) on the host.

```bash
docker compose up --build
```

Detached:

```bash
docker compose up -d --build
```

Stop:

```bash
docker compose down
```

Once running, the `curl` and `grpcurl` examples above work unchanged against
`localhost`.
