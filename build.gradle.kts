import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dependency.analysis)
    alias(libs.plugins.androidx.baselineprofile) apply false
    // Provides JavaToolchainService for the JDK 25 ktlint JavaExec tasks below.
    id("jvm-toolchains")
}

// ktlint CLI via JavaExec on JDK 25. The ktlint-gradle plugin was removed:
// its worker processes load kotlin-compiler-embeddable into the JDK 25 daemon
// and emit terminally-deprecated sun.misc.Unsafe::objectFieldOffset WARNINGs
// (JEP 498) that the daemon's own opt-in flags never reach. Running the CLI in
// a plain JavaExec keeps that compiler out of the build entirely.
val ktlintCli = configurations.create("ktlintCli") {
    // ktlint-cli publishes both external and shadowed variants; JavaExec needs
    // the shadowed fat jar (contains com.pinterest.ktlint.Main + deps).
    attributes {
        attribute(
            org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE,
            objects.named(org.gradle.api.attributes.Bundling.SHADOWED),
        )
    }
}

dependencies {
    ktlintCli(libs.ktlint.cli)
}

val javaToolchains = extensions.getByType(JavaToolchainService::class.java)

// Module-list independent on purpose: a newly added module is linted the day
// it appears, instead of silently escaping until someone updates this list.
val ktlintInputPatterns = listOf(
    "**/src/**/*.kt",
    "**/src/**/*.kts",
    "**/*.gradle.kts",
    "*.kts",
    "!**/build/**",
)

fun JavaExec.configureKtlint(extraArgs: List<String>) {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        },
    )
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    // ktlint-cli's shadowed jar embeds the IntelliJ PSI parser, which calls the
    // terminally deprecated sun.misc.Unsafe::objectFieldOffset. Opt in here
    // rather than relying on build.sh exporting JAVA_TOOL_OPTIONS, so a bare
    // ./gradlew ktlintCheck is warning-free too.
    jvmArgs("--sun-misc-unsafe-memory-access=allow", "--enable-native-access=ALL-UNNAMED")
    workingDir = rootDir
    args(extraArgs + ktlintInputPatterns + "--relative")
}

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "Check Kotlin sources with ktlint on JDK 25"
    configureKtlint(emptyList())
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Format Kotlin sources with ktlint on JDK 25"
    configureKtlint(listOf("-F"))
}
