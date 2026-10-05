ARG BASE_IMAGE=registry.access.redhat.com/ubi9/openjdk-21-runtime:latest

FROM ${BASE_IMAGE} AS layers
USER 0
WORKDIR /layers
COPY backend/build/libs/dso-portal-*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM ${BASE_IMAGE}
LABEL org.opencontainers.image.title="BBH DevSecOps Management Portal" \
      org.opencontainers.image.source="https://github.com/mateuszmatan/DSOMgmtPortal" \
      io.k8s.display-name="BBH DevSecOps Management Portal" \
      io.k8s.description="Manages DevSecOps products, services, pipelines and pipeline keys for DSOEnhanced" \
      io.openshift.expose-services="8080:http"
USER 0
WORKDIR /application
RUN mkdir -p /application/data && chown 185:0 /application/data && chmod 0775 /application/data
COPY --from=layers --chown=185:0 /layers/extracted/dependencies/ ./
COPY --from=layers --chown=185:0 /layers/extracted/spring-boot-loader/ ./
COPY --from=layers --chown=185:0 /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers --chown=185:0 /layers/extracted/application/ ./
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    PORT=8080
USER 185
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
