package honey.vcs.git

import java.io.File

object GitTest3 {
    @JvmStatic
    fun main(args: Array<String>) {
        val task = MavenPublishTask(
            repoUrl = "https://github.com/chaschev/kgit.git",
            buildDir = File("build"),
            projectName = "kgit",
            group = "honey",
            module = "kgit",
            version = "0.0.6"
        )

        task.initRepo()
        System.`in`.read()
        task.publishToGit()
    }
}