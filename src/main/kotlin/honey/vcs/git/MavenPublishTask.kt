package honey.vcs.git

import java.io.File

class MavenPublishTask (
    val repoUrl: String,
    val buildDir: File,
    val projectName: String,
    val group: String,
    val module: String = projectName,
    val version: String
)
{
    val repoPath = "$buildDir/${projectName}-repository"

    val versionPath = "$repoPath/${group.split('.').joinToString("/")}/$module/$version"

    val repoDir = File(repoPath)

    val git by lazy {Git(repoDir, repoUrl)}

    init{
        println("checking out repository branch...")
    }

    fun initRepo(){
        git.checkoutBranch("repository")
        println("repo was initialized")
    }

    fun publishToGit(){
        val addPath = versionPath.substring(repoPath.length + 1)

        println("adding path $addPath ($versionPath) to commit...")

        git.addAll(addPath)

        println(git.logShortStatus())

        git.commit("publish version $version")

        println("pushing to git...")

        git.push()
    }
}