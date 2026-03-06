function gradle_clean_test {
  ./gradlew clean
  ./gradlew test
}

cd multi_agent_ide_lib
gradle_clean_test

cd ../utilitymodule
gradle_clean_test

cd ../acp-cdc-ai
source tests.sh

cd ../multi_agent_ide
source tests.sh
