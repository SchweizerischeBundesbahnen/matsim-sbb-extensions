#!/bin/bash
#
# Helper script to create a new release
#
# This script performs the following steps:
# - create a new feature-branch
# - sets the release-version in the pom.xml
# - creates a commit with the release-version
# - creates a tag with the release-version
# - sets the new development-version in the pom.xml
# - creates a commit with the new development-version
#
# After that, the branch and tag are pushed to the remote repository.
# Now, two pull-requests can be created manually,
# one to merge to the develop-branch, and one to merge the tag to the master-branch.
#

# Advances the last number of the given version string by one.
# taken from https://stackoverflow.com/questions/3545292/how-to-get-maven-project-version-to-the-bash-command-line
function advance_version () {
    local v=$1
    # Get the last number. First remove any suffixes (such as '-SNAPSHOT').
    local cleaned=`echo $v | sed -e 's/[^0-9][^0-9]*$//'`
    local last_num=`echo $cleaned | sed -e 's/[0-9]*\.//g'`
    local next_num=$(($last_num+1))
    # Finally replace the last number in version string with the new one.
    echo $v | sed -e "s/[0-9][0-9]*\([^0-9]*\)$/$next_num/"
}

echo "Getting the current version. This may take a while the first time it's run."
CURRENT_VERSION=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec)

echo "Current version: $CURRENT_VERSION"

RELEASE_VERSION=${CURRENT_VERSION%-SNAPSHOT}
echo -n "Release version: [$RELEASE_VERSION] "
read IN
if [ -n "$IN" ]; then
    # non-empty input, so ue this as release version
    RELEASE_VERSION=$IN
fi

BRANCH=feature/release-$RELEASE_VERSION
TAG="v$RELEASE_VERSION"

NEXT_VERSION=$(advance_version $RELEASE_VERSION)
NEXT_VERSION="${NEXT_VERSION}-SNAPSHOT"
echo -n "Next version: [$NEXT_VERSION] "
read IN
if [ -n "$IN" ]; then
    # non-empty input, so use this as next version
    NEXT_VERSION=$IN
fi

DRYRUN=1
echo -n "Perform dry run? [Y/n] "
read IN
if [ -z "$IN" ]; then
    # empty input, assume 'y'
    IN="y"
fi
if [ "$IN" = "n" ]; then
    DRYRUN=0
fi

echo ""
echo "---------------------------------"

if [ $DRYRUN = 1 ]; then
    echo "[DRYRUN] dry run only, no actual commits or pushes will be performed."
    echo ""
fi

echo "Current version   : $CURRENT_VERSION"
echo "Release version   : $RELEASE_VERSION"
echo "Tag for Release   : $TAG"
echo "Next version      : $NEXT_VERSION "
echo "Branch for release: $BRANCH"
if [ $DRYRUN = 0 ]; then
    echo "Changes will be pushed to remote repository if you continue!";
fi

echo "Continue? [y/N]"
read CONTINUE

if [ "$CONTINUE" != "y" ]; then
    echo "Goodbye."
    exit 0
fi

echo "Creating new branch: $BRANCH"
git checkout -b $BRANCH

echo "Setting release version: $RELEASE_VERSION"
mvn -q versions:set -DnewVersion=$RELEASE_VERSION
mvn -o -q versions:commit  # gets rid of pom.xml.versionsBackup

echo "Create commit for release"
git add pom.xml
git commit -m "Create release: $RELEASE_VERSION"

echo "Creating tag"
git tag -a $TAG -m "release $RELEASE_VERSION"

echo "Setting new development version"
mvn -o -q versions:set -DnewVersion=$NEXT_VERSION
mvn -o -q versions:commit  # gets rid of pom.xml.versionsBackup

echo "Committing new development version"
git add pom.xml
git commit -m "Set next development version: $NEXT_VERSION"

echo "Pushing changes to remote"
if [ $DRYRUN = 0 ]; then
    git push origin $BRANCH
    git push origin $TAG
else
    echo "[DRYRUN] git push origin $BRANCH"
    echo "[DRYRUN] git push origin $TAG"
    echo "[DRYRUN]"
    echo "[DRYRUN] to revert back to the original state, use:"
    echo "[DRYRUN]    git checkout develop"
    echo "[DRYRUN]    git branch -D $BRANCH"
    echo "[DRYRUN]    git tag -d $TAG"
fi

echo "everything done."
