package honey.vcs.git

import java.io.File

object GitTest2 {
    @JvmStatic
    fun main(args: Array<String>) {
        val git = Git(File("out/test-repo"),
            "https://chaschev@bitbucket.org/chaschev/rocket-cloud.git")

//        git.fetch()
        git.lsRemote()!!.forEach { (k, _) -> println(k) }

        println("\nall branches")
        git.listAllBranches().forEach { println(it.name) }

        println("\nlocal branches")
        git.listAllBranches().forEach { println(it.name) }

        println("\nremote branches")
        git.listRemoteBranches().forEach { println(it.name) }

        git.checkoutBranch("release9")

    }
}