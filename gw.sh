#!/bin/sh
# 이 프로젝트 전용 빌드 실행: Gradle 캐시를 D 드라이브에 둠
export JAVA_HOME="/c/Program Files/Java/jdk-21"
export ANDROID_HOME="D:/Android/Sdk"
export GRADLE_USER_HOME="D:/Android/gradle-home"
cd "$(dirname "$0")" && ./gradlew "$@"
