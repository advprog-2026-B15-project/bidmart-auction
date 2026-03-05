FROM eclipse-temurin:21-jre-alpine

ARG USER_NAME=kurrorro
ARG USER_UID=1000
ARG USER_GID=${USER_UID}

RUN addgroup -g ${USER_GID} ${USER_NAME} \
    && adduser -h /opt/${USER_NAME} -D -u ${USER_UID} -G ${USER_NAME} ${USER_NAME}

USER ${USER_NAME}
WORKDIR /opt/${USER_NAME}

COPY --chown=${USER_UID}:${USER_GID} build/libs/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]