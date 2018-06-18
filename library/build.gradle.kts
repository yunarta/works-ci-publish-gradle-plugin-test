import com.mobilesolutionworks.gradle.publish.PublishedDoc
import com.mobilesolutionworks.gradle.publish.worksPublication
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    groovy
    jacoco
}

apply {
    plugin("kotlin")
    plugin("works-publish")
}

worksPublication?.apply {
    javadoc = PublishedDoc.Kotlin
    module = file("module.yaml")
}

val kotlinVersion: String by rootProject.extra
val sourceSets: SourceSetContainer = java.sourceSets

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(kotlin("stdlib-jdk8", kotlinVersion))
    implementation(kotlin("reflect", kotlinVersion))
}