#!/bin/bash
#
# Generates a git-remote-s3enc executable in /usr/local/bin which starts the main class.
#

echo "#!/usr/bin/java -jar $HOME/.m2/repository/sunday/git-remote-s3enc/0.1-SNAPSHOT/git-remote-s3enc-0.1-SNAPSHOT.jar" > /usr/local/bin/git-remote-s3enc
chmod 755 /usr/local/bin/git-remote-s3enc
