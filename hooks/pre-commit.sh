#!/bin/sh
path=$(git diff --name-only --cached | grep "TestCommand.java")
if [[ -n "$path" ]]; then
    echo unstage TestCommand.java
    git restore --staged $path
fi
usages=$(git diff  --cached *.java | grep TestCommand )
if [[ -n "$usages" ]]; then
    echo -e "Usages of TestCommand detected"
    git diff-index -STestCommand --cached -u HEAD *.java
    exit 1
fi
exit 0