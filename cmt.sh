a=$1
echo "Committing with $a"
branch=$2


git a
git commit -m "$a" || true
git push origin $2

cd acp-cdc-ai
git a
git commit -m "$a" || true
git push origin $2

cd ../multi_agent_ide
git a
git commit -m "$a" || true
git push origin $2

cd ../multi_agent_ide_lib
git a
git commit -m "$a" || true
git push origin $2

cd ../utilitymodule
git a
git commit -m "$a" || true
git push origin $2

echo "Done committing $a"


