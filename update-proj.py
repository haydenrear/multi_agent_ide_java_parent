import shutil
import xml.etree.ElementTree as ET
from pathlib import Path
import os

# ---- CONFIG ----
INTELLIJ_VERSION = "IntelliJIdea2024.3"  # <-- change if needed
REMOVE_PREFIX = "$USER_HOME$/.multi-agent-ide/worktrees"

# ---- PATH ----
# Located in ~/Library/Application Support/JetBrains/IntelliJIdea2025.3/options
xml_path = (
    "recentProjects.xml"
)

xml_path = os.path.join("/Users/hayde/Library/Application Support/JetBrains/IntelliJIdea2025.3/options", xml_path)

xml_path_out = (
    "recentProjectsOut.xml"
)

if not Path(xml_path).exists():
    raise FileNotFoundError(f"File not found: {xml_path}")

print(f"Cleaning: {xml_path}")

# Backup first
backup_path = Path(xml_path).with_suffix(".xml.bak")
shutil.copy2(xml_path, backup_path)
print(f"Backup created at: {backup_path}")

tree = ET.parse(xml_path)
root = tree.getroot()

removed_entries = 0

# Find RecentProjectsManager component
for component in root.findall("component"):
    if component.get("name") != "RecentProjectsManager":
        continue

    # ---- Remove entries inside map ----
    for option in component.findall("option"):
        if option.get("name") == "additionalInfo":
            map_tag = option.find("map")
            if map_tag is not None:
                for entry in list(map_tag.findall("entry")):
                    key = entry.get("key", "")
                    if key.startswith(REMOVE_PREFIX):
                        map_tag.remove(entry)
                        removed_entries += 1

        # ---- Remove lastOpenedProject if it matches ----
        if option.get("name") == "lastOpenedProject":
            value = option.get("value", "")
            if value.startswith(REMOVE_PREFIX):
                component.remove(option)
                print("Removed lastOpenedProject entry")

        # ---- Remove lastProjectLocation if it matches ----
        if option.get("name") == "lastProjectLocation":
            value = option.get("value", "")
            if value.startswith(REMOVE_PREFIX):
                component.remove(option)
                print("Removed lastProjectLocation entry")

# Write back cleaned XML
tree.write(xml_path_out, encoding="UTF-8", xml_declaration=True)

print(f"Removed {removed_entries} worktree entries.")
print("Done.")
