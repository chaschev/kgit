package rocket.vcs.git

import java.io.File
import java.util.*

/**
 * Git.clone
 * Git.init
 */

open class PropertiesConfig(file: File){
    val authProps = Properties()

    init {
        file.inputStream().use {
            authProps.load(it)
        }
    }

    operator fun get(name: String): String? {
        return  authProps.getProperty(name) ?:
                System.getenv(name)
    }
}