echo "Starting unit tests."
./gradlew test
echo "Starting integration-y tests."
./gradlew test -Pprofile=integration
#echo "Starting acp-integration-y tests."
#./gradlew test -Pprofile=acp-integration

