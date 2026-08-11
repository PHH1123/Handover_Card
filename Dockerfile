# syntax=docker/dockerfile:1

# ---------- build ----------
# 빌드 툴체인이 JDK 25로 고정되어 있어(build.gradle) 빌드 이미지도 25를 쓴다.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY src src

# 테스트는 CI에서 돌린다. 이미지 빌드는 산출물만 만드는 단계라 여기서 다시 돌리지 않는다.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar -x test

# build/libs에는 실행 가능한 jar와 클래스만 든 -plain.jar가 같이 나온다. 글롭으로 집으면
# -plain.jar가 먼저 잡혀 "no main manifest attribute"로 죽으므로 실행 가능한 쪽을 명시적으로 고른다.
RUN cp "$(find build/libs -name '*.jar' ! -name '*-plain.jar' -print -quit)" app.jar

# 실행 계층을 분리해서 꺼낸다. 의존성 계층은 소스만 바뀐 배포에서 그대로라 이미지 전송량이 줄어든다.
RUN java -Djarmode=tools -jar app.jar \
    extract --layers --launcher --destination extracted

# ---------- runtime ----------
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN groupadd --system handover \
    && useradd --system --gid handover --home-dir /app --shell /usr/sbin/nologin handover

# 변경이 드문 계층부터 복사해야 캐시가 산다.
COPY --from=build --chown=handover:handover /workspace/extracted/dependencies/ ./
COPY --from=build --chown=handover:handover /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=handover:handover /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=handover:handover /workspace/extracted/application/ ./

USER handover

ENV SPRING_PROFILES_ACTIVE=prod
# 컨테이너에 할당된 메모리 기준으로 힙을 잡는다. 고정 -Xmx와 달리 인스턴스 크기를 바꿔도 손댈 게 없다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# sh -c를 거치는 이유는 JAVA_OPTS를 배포 시점에 덧붙일 수 있게 하기 위함이고,
# exec를 붙여야 자바가 PID 1이 되어 SIGTERM을 직접 받는다(graceful shutdown에 필요).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
