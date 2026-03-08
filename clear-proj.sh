echo "Did you stop intellij? y/n"
read yes

if [ $yes == "y" ]; then
	cd "/Users/hayde/Library/Application Support/JetBrains/IntelliJIdea2025.3/options"
	python ~/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/update-proj.py
	mv recentProjectsOut.xml recentProjects.xml
else
	echo "Stop Intellij first."
fi
