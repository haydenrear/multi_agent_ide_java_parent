echo "Starting unit tests."
./gradlew test
echo "Starting integration-y tests."
./gradlew test -Pprofile=integration

