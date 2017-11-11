package honey.vcs.git

import org.eclipse.jgit.revwalk.RevCommit

fun RevCommit.describe() = "last commit: $fullMessage $id"

public inline fun <T : AutoCloseable?, R> T.use(block: (T) -> R): R {
    var exception: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        this.closeFinally(exception)
    }
}

fun AutoCloseable?.closeFinally(cause: Throwable?) = when {
    this == null -> {
    }
    cause == null -> close()
    else ->
        try {
            close()
        } catch (closeException: Throwable) {
            cause.addSuppressed(closeException)
        }
}