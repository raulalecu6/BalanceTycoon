// Archivo de compilación raíz: declara los plugins sin aplicarlos; cada módulo aplica los suyos.
// Con AGP 9 el soporte de Kotlin viene integrado en el plugin de Android, así que el módulo app
// no aplica org.jetbrains.kotlin.android. El módulo core es Kotlin puro (plugin kotlin-jvm).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
