#!/bin/bash
#
# Generates a git-remote-local executable in /usr/local/bin which starts the main class.
#

echo "#!/usr/bin/java -jar $HOME/.m2/repository/sunday/git-remote-local/0.1-SNAPSHOT/git-remote-local-0.1-SNAPSHOT.jar" > /usr/local/bin/git-remote-local
chmod 755 /usr/local/bin/git-remote-local
