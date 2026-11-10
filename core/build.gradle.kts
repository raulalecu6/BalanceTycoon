// Motor del juego en Kotlin puro: contabilidad, fiscalidad, economía y guardado.
// No depende de Android, así que sus tests se ejecutan en segundos sin emulador.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    // Las mediciones de equilibrio imprimen resultados: se muestran al ejecutar los tests.
    testLogging.showStandardStreams = true
}
