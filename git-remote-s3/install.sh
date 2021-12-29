#!/bin/bash
#
# Generates a git-remote-s3 executable in /usr/local/bin which starts the main class.
#

echo "#!/usr/bin/java -jar $HOME/.m2/repository/sunday/git-remote-s3/0.1-SNAPSHOT/git-remote-s3-0.1-SNAPSHOT.jar" > /usr/local/bin/git-remote-s3
chmod 755 /usr/local/bin/git-remote-s3
