// Raise Gradle-managed AVD RAM/CPU before every *Setup. sdklib caps GMD guests
// at 2-3G / 2 cores (EmulatedProperties); Pixel 7a hardware is 8G / 8 cores.
// A starved google_apis guest LMKs the app during androidx.benchmark's 5s
// foreground wait, so SAVE_PROFILE sees processCount=0 and the collector
// throws "never flushed profiles". See scripts/gmd_ensure_avd.sh.
// Identical copy lives in dustvalve_next, calc, compass, and core.

val gmdAvdScript = rootDir.resolve("scripts/gmd_ensure_avd.sh").absolutePath

val gmdEnsureAvd =
    tasks.register("gmdEnsureAvd") {
        group = "verification"
        description =
            "Patch Gradle-managed AVD RAM/CPU so ART profile flush is not LMK'd"
        doLast {
            val proc =
                ProcessBuilder("bash", gmdAvdScript)
                    .inheritIO()
                    .start()
            val code = proc.waitFor()
            if (code != 0) {
                throw GradleException("gmd_ensure_avd.sh exited $code")
            }
        }
    }

gradle.projectsEvaluated {
    allprojects {
        tasks.configureEach {
            if (!name.endsWith("Setup")) return@configureEach
            if (!name.contains("Api")) return@configureEach
            dependsOn(gmdEnsureAvd)
            // Setup is create-and-start. If gmdEnsureAvd just stopped qemu
            // (RAM/CPU changed), an UP-TO-DATE skip would leave no guest.
            outputs.upToDateWhen { false }
        }
    }
}
