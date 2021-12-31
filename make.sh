#!/bin/bash
#
# Builds the application
#
cp git-remote-local/git-remote-local /usr/local/bin/
cp git-remote-s3/git-remote-s3 /usr/local/bin/
cp git-remote-s3enc/git-remote-s3enc /usr/local/bin/
mvn clean install
