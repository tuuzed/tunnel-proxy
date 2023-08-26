plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
    api(project(":core:common"))
    api(Libs.slf4j_log4j12)
    testImplementation(Libs.junit)
}
