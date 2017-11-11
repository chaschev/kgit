package honey.vcs.git

import mu.KLogging
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.PullResult
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.api.Git as JGit
import java.io.File
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import rocket.vcs.git.authProps
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.OutputStream


class Git(
    val repoDir: File,
    val url: String
) {
    companion object : KLogging()

    protected val git: JGit by lazy {
        if (!repoDir.exists()) {
            val git = JGit.cloneRepository()
                .setURI(url)
                .setDirectory(repoDir)
                .setCredentialsProvider(credentials)
                .call()

            logger.info { "cloned $url into ${git.repository.directory}" }

            git
        } else {
            val builder = FileRepositoryBuilder()

            val repo = builder.setGitDir(File(repoDir, ".git"))
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build()

            logger.info { "opened local repo ${repo.directory}" }

            JGit(repo)
        }

    }

    val credentials = UsernamePasswordCredentialsProvider(
        authProps["git.username"], authProps["git.password"]
    )

    fun listBranches(): MutableList<Ref> = git.branchList().call()

    fun checkoutBranch(
        branch: String,
        create: Boolean = false,
        fetch: Boolean = true
    ) {

        if (fetch) {
            fetch()
        }

        val co = git.checkout()
            .setName(branch)
            .setCreateBranch(create)
            .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
            .setStartPoint("origin/$branch")
            .call()

        logger.info { "checked out branch $branch objectId=${co.objectId}" }
    }

    fun fetch() {
        logger.info { "fetching repo $repoDir..." }
        val fetchResult = git.fetch()
            .setCredentialsProvider(credentials)
            .setCheckFetchedObjects(true)
            .call()

        logger.info { "fetch result: ${fetchResult.trackingRefUpdates}, messages: ${fetchResult.messages}" }
    }

    fun <T> withBranch(branch: String, create: Boolean = false, block: () -> T): T {
        val currentBranch = getBranch()

        return if (branch != currentBranch) {
            try {
                checkoutBranch(branch, create)
                block()
            } finally {
                checkoutBranch(currentBranch)
            }
        } else {
            block()
        }
    }

    fun lsRemote(): MutableMap<String, Ref>? {
        return git.lsRemote()
            .setCredentialsProvider(credentials)
            .callAsMap()
    }

    fun addFile(file: File): DirCache {
        if (!file.exists()) throw FileNotFoundException(file.path)

        val filepath = file.absolutePath
        val repopath = repoDir.absolutePath

        if (!filepath.startsWith(repopath))
            throw FileNotFoundException("$file must be in the repo dir: ${repoDir.absolutePath}")

        val r = git.add()
            .addFilepattern(filepath.substringAfter(repopath + "/"))
            .call()

        return r
    }

    fun commit(message: String): RevCommit {
        val r = git.commit()
            .setAll(true)
            .setMessage(message)
            .setCommitter(authProps["git.committer"], authProps["git.email"])
            .call()

        logger.info { "commit: ${r.fullMessage} id: ${r.id}" }

        return r
    }

    fun pull(): PullResult? {
        val r = git
            .pull()
            .setCredentialsProvider(credentials)
            .call()

        logger.info { "pull result ok: ${r.isSuccessful}, fetched from ${r.fetchedFrom}, merge ok: ${r.mergeResult.mergeStatus.name} ${r.mergeResult.mergeStatus.name}" }

        return r
    }

    fun push(pushAll: Boolean = true, force: Boolean = true) {
        val push = git.push()

        if (pushAll) push.setPushAll()

        push
            .setCredentialsProvider(credentials)
            .setForce(force)

        for (r in push.call()) {
            logger.info { "push messages: ${r.messages}, ${r.trackingRefUpdates}" }
        }
    }

    fun getBranch() = git.repository.fullBranch!!

    fun readFileFromBranch(path: String, branch: String, overwrite: Boolean = true, dest: File? = null): File {
        return withBranch(branch) {
            readOneFile(path, overwrite, dest)
        }
    }

    fun readOneFile(path: String, overwrite: Boolean = true, dest: File? = null): File {
        val destFile = dest ?: File(repoDir, path)

        if (!overwrite && destFile.exists()) throw Exception("file exists: $destFile")

        destFile.absoluteFile.parentFile.mkdirs()

        logger.info { "saving $path to $destFile" }
        FileOutputStream(destFile).use { out ->
            readOneFile(path, out)
        }

        return destFile
    }

    fun readOneFile(path: String, out: OutputStream) {
        val lastCommitId = git.repository.resolve(Constants.HEAD)

        // a RevWalk allows to walk over commits based on some filtering that is defined
        RevWalk(git.repository).use({ revWalk ->
            val commit = revWalk.parseCommit(lastCommitId)

            logger.debug { "last commit: ${commit.describe()}" }

            // and using commit's tree find the path
            val tree = commit.tree

            // now try to find a specific file
            TreeWalk(git.repository).use({ treeWalk ->
                treeWalk.addTree(tree)
                treeWalk.isRecursive = true
                treeWalk.filter = PathFilter.create(path)
                if (!treeWalk.next()) {
                    throw IllegalStateException("Did not find expected file $path")
                }

                val objectId = treeWalk.getObjectId(0)
                val loader = git.repository.open(objectId)

                // and then one can the loader to read the file
                loader.copyTo(out)
            })

            revWalk.dispose()
        })
    }


    fun exactRef(ref: String): Ref? = git.repository.exactRef(ref)
}



/*

def head = repo.exactRef("refs/heads/release")

    println("Ref of refs/heads/release: " + head)
 */