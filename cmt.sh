a=$1
echo "Committing with $a"

git a
git commit -m "$a" || true

cd acp-cdc-ai
git a
git commit -m "$a" || true

cd ../multi_agent_ide
git a
git commit -m "$a" || true

cd ../multi_agent_ide_lib
git a
git commit -m "$a" || true

cd ../utilitymodule
git a
git commit -m "$a" || true

echo "Done committing $a"


