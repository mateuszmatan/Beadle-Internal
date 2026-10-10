ARG BASE_IMAGE=registry.access.redhat.com/ubi9/openjdk-17-runtime:latest

FROM ${BASE_IMAGE} AS layers
USER 0
WORKDIR /layers
COPY dso-backend/build/libs/dso-portal-*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM ${BASE_IMAGE}
LABEL org.opencontainers.image.title="BBH DevSecOps Management Portal" \
      org.opencontainers.image.source="https://github.com/mateuszmatan/DSOMgmtPortal" \
      io.k8s.display-name="BBH DevSecOps Management Portal" \
      io.k8s.description="Manages DevSecOps products, services, pipelines and pipeline keys and serves the configuration of DSOEnhanced" \
      io.openshift.expose-services="8080:http"
USER 0
WORKDIR /application
RUN mkdir -p /application/data && chown 185:0 /application/data && chmod 0775 /application/data
COPY --from=layers --chown=185:0 /layers/extracted/dependencies/ ./
COPY --from=layers --chown=185:0 /layers/extracted/spring-boot-loader/ ./
COPY --from=layers --chown=185:0 /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers --chown=185:0 /layers/extracted/application/ ./
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    DSO_DATA_DIR=/application/data \
    PORT=8080
VOLUME /application/data
USER 185
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
