./gradlew bootJar
SPRING_PROFILES_ACTIVE=goose,cli java -jar build/libs/multi_agent_ide-0.0.1-SNAPSHOT.jar tui
