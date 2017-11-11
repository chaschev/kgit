# KGit

Lightweight Git Client written in Kotlin Language.

KGit is used to make basic Git operations simple. 

When things get tricky, you may fall back to JGit which is used under the hood.

Right I use in my Gradle script to upload jars to Git repository. It is also used in the lightweight orchestration tool I am currently developing.

```kotlin
val git = Git(url, localRepo)

git.checkoutBranch("master")

git.withBranch("release") {
    git.readOneFile("README.md")
}

git.commit("added README.md from release branch")
git.push()
```