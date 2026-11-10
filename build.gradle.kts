plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}


// CI diagnostics: surface compiler errors as GitHub Actions annotations (harmless locally).
allprojects {
    tasks.configureEach {
        logging.addStandardErrorListener { msg: CharSequence ->
            msg.lines()
                .filter { it.startsWith("e: ") || it.contains(": error:") }
                .forEach { println("::error::" + it.trim().take(900)) }
        }
    }
}
