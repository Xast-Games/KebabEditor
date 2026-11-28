plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":editor"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "org.xast.kebab.KebabApplication"

    // Name of the launcher script and of the installDist folder (build/install/kebab-editor).
    applicationName = "kebab-editor"

    // JOGL loads native libraries and reaches into AWT internals for its GL surfaces.
    // sun.awt.X11 is opened so AppIdentity can set the window class the .desktop file matches.
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "--add-exports=java.base/java.lang=ALL-UNNAMED",
        "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"
    )
}
