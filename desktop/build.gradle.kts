plugins {
    application
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

dependencies {
    implementation(project(":core"))
    implementation("com.google.code.gson:gson:2.13.2")
}

application { mainClass.set("com.ultron.desktop.UltronDesktop") }
