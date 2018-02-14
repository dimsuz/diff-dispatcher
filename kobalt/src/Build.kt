import com.beust.kobalt.*
import com.beust.kobalt.plugin.packaging.*
import com.beust.kobalt.plugin.application.*
import com.beust.kobalt.plugin.apt.kapt

const val kotlinVersion = "1.2.10"

val projectAnnotations = project {
    name = "diff-dispatcher-annotations"
    group = "com.dimsuz"
    artifactId = name
    version = "0.1"

    directory = "annotations"


    dependencies {
    }

    assemble {
        jar {

        }
    }

}

val projectProcessor = project(projectAnnotations) {
    name = "diff-dispatcher-processor"
    group = "com.dimsuz"
    artifactId = name
    version = "0.1"

    directory = "processor"


    dependencies {
        compile("org.jetbrains.kotlin:kotlin-runtime:$kotlinVersion")
        compile("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    }

    assemble {
        jar {

        }
    }
}

val projectSample = project(projectAnnotations) {
    name = "diff-dispatcher-sample"
    group = "com.dimsuz"
    artifactId = name
    version = "0.1"
    directory = "sample"

    dependencies {
        compile("org.jetbrains.kotlin:kotlin-runtime:$kotlinVersion")
        compile("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    }

    assemble {
        jar {
        }
    }

    application {
        mainClass = "com.dimsuz.diffdispatcher.sample.MainKt"
    }

    kapt {
    }
}
