package honey.vcs.git

import java.io.File

object GitTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val git = Git(File("out/test-repo"),
            "https://chaschev@bitbucket.org/chaschev/rocket-cloud.git")

        git.checkoutBranch("release")
//        git.fetch()
        git.pull()

        git.readFileFromBranch(
            path = "honey-badger-0.0.4.jar",
            branch = "release",
            dest = File("lib/honey-badger-0.0.4.jar")
        )

        git.readFileFromBranch("honey-badger-0.0.1.jar", "release")

        println("current: branch should have stayed master: ${git.getBranch()}")

        git.checkoutBranch("release")

        Runtime.getRuntime().exec("cp file.txt out/test-repo/file.txt")

        git.addFile(File("out/test-repo/file.txt"))

        git.commit("add file example")
        git.push()
    }
}