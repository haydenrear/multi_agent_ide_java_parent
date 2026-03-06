a=$1
echo "Committing with $a"

if [[ $a=="" ]]; then
  a="Committing to a commit."
fi

function run_cmt {
  echo "Updating branch"
  branch=$(git branch --show-current)
  echo "Checking status of $(pwd)"
  git status
  git add .
  echo "Committing $(pwd)"
  git commit -m "$a" || true
  echo "Pushing $(pwd) to "
  git push origin $branch
}


cd acp-cdc-ai
run_cmt

cd ../multi_agent_ide
run_cmt

cd ../multi_agent_ide_lib
run_cmt

cd ../utilitymodule
run_cmt

cd ..
run_cmt

echo "Done committing $a"


