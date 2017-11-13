package honey.vcs.git

import mu.KLogging
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.*
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.PullResult
import org.eclipse.jgit.api.Status
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
import org.eclipse.jgit.transport.RefSpec
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes


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

    fun listAllBranches(): MutableList<Ref> = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call()
    fun listRemoteBranches(): MutableList<Ref> = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call()

    fun listLocalBranches(): List<Ref> {
        // or just
//        return git.branchList().call()

        val remoteList = listRemoteBranches()
        val allList = listAllBranches()

        return allList.filter { x ->
            remoteList.firstOrNull { it.name == x.name } == null
        }
    }

    //local branches are refs/heads/*
    //remote branches ("remotes") are refs/remotes/origin/

    fun getLocalBranch(branch: String): Ref? = listLocalBranches().firstOrNull { it.name == "refs/heads/$branch" }

    fun getRemoteBranch(branch: String): Ref? = listRemoteBranches().firstOrNull { it.name == "refs/remotes/origin/$branch" }


    fun checkoutBranch(
        branch: String,
        create: Boolean? = null,
        fetch: Boolean = false,
        createIfNotExists:Boolean = true,
        createEmpty: Boolean = true
    ) {

        if (fetch) {
            fetch()
        }

        val remoteBranch = getRemoteBranch(branch)
        val localBranch = getLocalBranch(branch)

        if(remoteBranch == null){
            if(createIfNotExists) {
                val skipDeleteList = repoDir.list()

                val spec = RefSpec("refs/heads/master:refs/heads/$branch")
                git.push()
                    .setRefSpecs(spec)
                    .setCredentialsProvider(credentials)
                    .call()

                if(createEmpty) {
                    //todo make more smart
                    emptyGitDir(skipDeleteList)
                    commit("create an empty branch $branch")
                }

                return
            } else {
                throw Exception("branch $branch does not exist, specify createIfExists=true to auto-create")
            }
        }

        val createFlag = create ?: localBranch == null
            //default
            val co = git.checkout()
                .setName(branch)
                .setCreateBranch(createFlag)
                .setUpstreamMode(TRACK)
                .setForce(true)
                .setStartPoint("origin/$branch")
                .call()

            logger.info { "checked out branch $branch objectId=${co.objectId}" }
//        }
    }

    private fun emptyGitDir(skipDeleteList: Array<String>) {
        val repoPath = repoDir.toPath()

        Files.walkFileTree(repoPath, object : SimpleFileVisitor<Path>() {
            @Throws(IOException::class)
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes?): FileVisitResult {
                val dirName = dir.toFile().name //fuck you Java

                if(dirName == ".git") return FileVisitResult.SKIP_SUBTREE

                if(dir.parent == repoDir){
                    if(skipDeleteList.contains(dir.toFile().name)) {
                        println("skip $dir")
                        return FileVisitResult.SKIP_SUBTREE
                    }
                }

                return FileVisitResult.CONTINUE
            }

            @Throws(IOException::class)
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (dir != repoPath) {
//                    println("deleting $dir")
                    Files.delete(dir)
                }

                return FileVisitResult.CONTINUE
            }
        })
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

    fun getBranches(): MutableMap<String, Ref>? {
        return git.lsRemote()
            .setCredentialsProvider(credentials)
            .setTags(false)
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

    fun status(): Status {
        return git.status().call()
    }

    fun logShortStatus(): String {
        val status = status()

        return "modified: ${status.modified.size}, added: ${status.added.size}, removed: ${status.removed.size}"
    }

    fun addAll(path: String): DirCache {
        val r = git.add()
            .addFilepattern(path)
            .call()

        logger.debug { "found ${r.entryCount} in git add" }

//        for(i in 0 until r.entryCount) {
//            println("A ${r.nextEntry(i)}")
//        }

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