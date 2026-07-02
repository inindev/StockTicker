package com.github.premnirmal.gradle

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Project

fun Project.execAndGetStdout(vararg args: String): String =
  providers.exec {
    commandLine(*args)
  }.standardOutput.asText.get().trim()

fun Project.getVersionNameFromGit(): String =
  try {
    execAndGetStdout("git", "describe", "--tags", "--abbrev=0")
  } catch (e: Exception) {
    // No reachable tag (fresh clone, tags not fetched, or shallow checkout). Fall back to a
    // valid X.Y.Z so the versionName/versionCode parsing in app/build.gradle.kts still works.
    // Mirrors the iOS fallback in iosApp/version.sh.
    println("No git tag found, defaulting version to $FALLBACK_VERSION: ${e.message}")
    FALLBACK_VERSION
  }

private const val FALLBACK_VERSION = "4.0.0"

fun Project.getOldGitVersionFromGit(): String =
  try {
    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
      execAndGetStdout("powershell", "-command", "git tag --sort=-committerdate | Select-Object -first 15 | Select-Object -last 1")
    } else {
      execAndGetStdout("sh", "-c", "git tag --sort=-committerdate | head -20 | tail -1")
    }
  } catch (e: Exception) {
    println(e.message)
    println(e.stackTrace)
    "1.0"
  }

fun Project.getCommitsBetween(old: String, new: String): String =
  try {
    val log =
      if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        execAndGetStdout("powershell", "-command", "git log --pretty=format:\"%s\" $old...$new")
      } else {
        execAndGetStdout("sh", "-c", "git log --pretty=format:\"%s\" $old...$new")
      }
    log.replace("\n", "\\n")
  } catch (e: Exception) {
    // The tag range doesn't exist (e.g. no tags on a fresh clone). Degrade to an empty changelog
    // rather than breaking the build; a tagless checkout simply has no "what's new" entries.
    println("Could not read commits between $old and $new, using empty changelog: ${e.message}")
    ""
  }

