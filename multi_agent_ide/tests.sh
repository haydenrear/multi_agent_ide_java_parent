echo "Starting unit tests."
./gradlew test
echo "Starting unit tests."
./gradlew test -Pprofile=shell-integration
echo "Starting integration-y tests."
./gradlew test -Pprofile=integration
#echo "Starting acp-integration-y tests."
#./gradlew test -Pprofile=acp-integration

