cd multi_agent_ide
./gradlew bootJar
cd ..
SPRING_PROFILES_ACTIVE=codex,cli java -jar multi_agent_ide/build/libs/multi_agent_ide-0.0.1-SNAPSHOT.jar tui
