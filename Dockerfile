FROM ghcr.io/graalvm/native-image-community:21 AS build

WORKDIR /workspace

RUN microdnf install -y findutils \
    && microdnf clean all

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew nativeCompile --no-daemon
RUN native_binary="$(find build/native/nativeCompile -maxdepth 1 -type f -perm -111 -print -quit)" \
    && test -n "${native_binary}" \
    && cp "${native_binary}" /workspace/app

FROM debian:bookworm-slim

WORKDIR /app

COPY --from=build /workspace/app app

EXPOSE 8080

ENTRYPOINT ["/app/app"]
