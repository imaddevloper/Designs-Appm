# DisguiseApp
## Android Security Research, Malware Analysis & Defensive Assessment

<p align="center">
  <strong>Kotlin · Android SDK · Gradle · GitHub Actions</strong>
</p>

<p align="center">
  A source-level Android security research project for examining mobile threat patterns,
  permission boundaries, background execution, accessibility exposure, and defensive detection.
</p>

---

> **Security notice**
>
> The repository contains security-sensitive Android components associated with remote
> control, device sensors, file access, accessibility, and background persistence. This
> documentation describes the project for source review, malware analysis, and defensive
> education. It is not authorization to install, deploy, or operate the application on
> another person's device. Use only isolated laboratory devices and systems for which
> you have explicit, informed authorization.

## Contents

- [1. Project overview](#1-project-overview)
- [2. Research goals](#2-research-goals)
- [3. Project profile](#3-project-profile)
- [4. Repository architecture](#4-repository-architecture)
- [5. Component inventory](#5-component-inventory)
- [6. Android platform surface](#6-android-platform-surface)
- [7. Technology stack](#7-technology-stack)
- [8. Build configuration](#8-build-configuration)
- [9. CI workflow and current build status](#9-ci-workflow-and-current-build-status)
- [10. Defensive analysis methodology](#10-defensive-analysis-methodology)
- [11. Security, privacy, and laboratory requirements](#11-security-privacy-and-laboratory-requirements)
- [12. Known limitations and verification status](#12-known-limitations-and-verification-status)
- [13. Responsible-use policy](#13-responsible-use-policy)
- [14. License and attribution](#14-license-and-attribution)

---

## 1. Project overview

**DisguiseApp** is an Android application project implemented primarily in Kotlin. Its
source tree brings together several security-sensitive Android capabilities in one
application, including background components, command-processing code, network-related
classes, accessibility integration, and components associated with device resources.

From a cybersecurity perspective, the repository is suitable for **source-code review,
threat modeling, static analysis, controlled behavioral observation, and defensive
detection research**. Its structure provides examples of Android platform features that
security analysts commonly need to understand when investigating potentially unwanted
or malicious mobile applications.

The project should be treated as potentially harmful software until each sensitive
behavior has been independently reviewed and constrained.

### What this project is — and is not

| Classification | Description |
|---|---|
| Project type | Android/Kotlin security research codebase |
| Primary analysis focus | Mobile threat behavior and defensive assessment |
| Platform | Android |
| Build system | Gradle |
| Main source language | Kotlin |
| Current release readiness | Not verified; latest inspected build failed |
| Safe deployment assumption | Laboratory-only, with explicit authorization |

The repository's source code is not, by itself, proof that every listed capability works
on every Android version or device. Runtime behavior depends on Android version,
permissions, manufacturer restrictions, configuration, and the implementation details
of each component.

## 2. Research goals

The project can support learning and assessment in the following areas.

### 2.1 Android application security

- Review application components and their exported status.
- Understand how Android permissions are declared and requested.
- Examine foreground-service declarations and platform restrictions.
- Assess the security implications of accessibility services.
- Review application lifecycle and background execution behavior.
- Identify excessive permissions and unnecessary platform access.

### 2.2 Mobile threat analysis

- Recognize common architectural patterns found in Android malware.
- Map sensitive components to potential security impacts.
- Build a threat model from source code and manifest declarations.
- Distinguish declared capabilities from verified runtime behavior.
- Document indicators that defenders may investigate.

### 2.3 Defensive security

- Review potentially sensitive network communication paths.
- Identify privacy risks involving device resources and user data.
- Evaluate persistence-related platform entry points.
- Develop static-analysis and code-review checklists.
- Prepare findings in a reproducible security assessment report.

These objectives do not require covert collection, unauthorized access, or deployment
against third-party devices.

## 3. Project profile

| Property | Repository observation |
|---|---|
| Repository | `imaddevloper/Designs-Appm` |
| Application namespace | `com.example.disguise` |
| Main activity | `MainActivity` |
| Language | Kotlin |
| UI approach | Android XML layouts with View Binding |
| Minimum SDK | 30 |
| Target SDK | 35 |
| Compile SDK | 35 |
| Java compatibility | 17 |
| Kotlin JVM target | 17 |
| Build automation | GitHub Actions |
| Gradle version documented by repository | 8.9 |
| Android Gradle Plugin documented by repository | 8.7.3 |

Values above describe the repository configuration inspected for this document. They
should be checked again whenever the build files or workflow are changed.

## 4. Repository architecture

The following tree summarizes the principal source files observed in the repository.
It is an architectural map, not an execution guide.

```text
Designs-Appm/
├── .github/
│   └── workflows/
│       └── release-apk.yml
├── app/
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/example/disguise/
│           │   ├── MainActivity.kt
│           │   ├── control/
│           │   │   ├── AccessibilityHelper.kt
│           │   │   ├── CameraExfil.kt
│           │   │   ├── MicExfil.kt
│           │   │   └── ScreenReader.kt
│           │   ├── core/
│           │   │   ├── C2Service.kt
│           │   │   ├── CommandHandler.kt
│           │   │   ├── Protocol.kt
│           │   │   ├── RestartJobService.kt
│           │   │   ├── TelegramController.kt
│           │   │   └── TelegramReporter.kt
│           │   ├── files/
│           │   │   └── FileExfil.kt
│           │   ├── persist/
│           │   │   └── BootReceiver.kt
│           │   └── util/
│           │       ├── ActivityTracker.kt
│           │       ├── Notifier.kt
│           │       └── PermissionHelper.kt
│           └── res/
├── README.md
└── [Gradle project files]
```

Some resource and root-level files are omitted from this overview to keep the map focused
on the main application architecture.

## 5. Component inventory

This section explains the apparent responsibility suggested by each source filename and
the repository manifest. A filename is not sufficient evidence of actual runtime
behavior; each implementation must be reviewed before making a definitive claim.

### 5.1 Application entry point

**`MainActivity.kt`**

The main Android activity and user-interface entry point. The inspected source imports
Android lifecycle, permission, UI, and application-service classes. It also references
the activity-tracking utility and the application's background service.

Review topics:

- Activity lifecycle handling.
- Permission request flow and user-facing disclosure.
- Whether the UI accurately explains sensitive capabilities.
- Whether background work is visible and controllable by the device owner.
- Whether all sensitive actions require a clear user-initiated action.

### 5.2 Control-related package

**`control/AccessibilityHelper.kt`**

An Android accessibility-service component. Accessibility services are highly privileged
from a privacy perspective because they may interact with or observe interface content,
subject to Android's rules and the service configuration.

Defensive review should verify that the service has a narrow, disclosed purpose and does
not collect credentials, private messages, or unrelated screen content.

**`control/ScreenReader.kt`**

A screen-reading-related component. Review its data scope, event handling, retention,
and whether the user can clearly enable and disable it.

**`control/CameraExfil.kt`**

A camera-related component. The name indicates a sensitive camera/data-handling area.
Review should establish whether camera access is visible, consent-based, foregrounded,
and limited to an explicitly requested test action.

**`control/MicExfil.kt`**

A microphone-related component. Audio capture is highly sensitive. Any legitimate
research implementation must provide conspicuous notice, explicit consent, clear
recording state, and an immediate stop control. Covert recording is not an acceptable
test procedure.

### 5.3 Core package

**`core/C2Service.kt`**

A service whose name indicates command-and-control-related background communication.
This is a high-risk architectural area. Defensive review should focus on identifying
remote-control exposure, authentication weaknesses, transport security, logging,
and whether the component can be disabled reliably.

**`core/CommandHandler.kt`**

A command-processing component. It is security-critical because command dispatch can
connect external input to local application behavior. Review should verify strict
authorization, input validation, least privilege, and safe rejection of unknown
commands.

**`core/Protocol.kt`**

A protocol-related source file. Review should document message formats at a high level,
authentication assumptions, error handling, and data minimization. Do not publish
credentials, tokens, private endpoints, or operational command examples.

**`core/TelegramController.kt` and `core/TelegramReporter.kt`**

Integration and reporting components whose names indicate Telegram-related
communication. Review should identify what metadata is transmitted, how credentials
are stored, whether recipients are controlled by the device owner, and whether
communication can be disabled. Never commit bot tokens or other secrets.

**`core/RestartJobService.kt`**

A background job service. Review should verify that scheduled work is necessary,
transparent to the user, compliant with Android background-execution rules, and
stoppable through a clear user-facing control.

### 5.4 File-related package

**`files/FileExfil.kt`**

A file-access/data-transfer-related component. This is a high-risk privacy boundary.
A safe research implementation should use synthetic test files in a dedicated lab
directory, avoid broad storage access, and never transfer personal files.

### 5.5 Persistence-related package

**`persist/BootReceiver.kt`**

A boot-event receiver. Boot receivers can cause an application to react after a device
restarts. Defensive review should determine whether the behavior is necessary, disclosed,
limited, and easy for the device owner to disable.

### 5.6 Utility package

| File | Review focus |
|---|---|
| `util/ActivityTracker.kt` | Activity lifecycle references, memory safety, and cleanup |
| `util/Notifier.kt` | User-visible notifications and disclosure |
| `util/PermissionHelper.kt` | Permission checks, rationale, and least privilege |

## 6. Android platform surface

The inspected manifest declares permissions and components that deserve careful
security review.

### 6.1 Declared permission groups

| Permission / capability | Security significance |
|---|---|
| `INTERNET` | Allows network communication |
| `ACCESS_NETWORK_STATE` | Allows checking network connectivity |
| `CAMERA` | Grants access to the camera after runtime approval |
| `RECORD_AUDIO` | Grants access to microphone input after runtime approval |
| Foreground-service permissions | Govern eligible foreground service operation |
| `RECEIVE_BOOT_COMPLETED` | Allows receiving the boot-completed broadcast |
| `MANAGE_EXTERNAL_STORAGE` | Broad access to shared external storage, subject to Android rules |
| `POST_NOTIFICATIONS` | Allows posting notifications on supported Android versions |
| `WAKE_LOCK` | Allows keeping the device awake under specified conditions |

A declared permission does not mean permission was granted, that the corresponding
feature works, or that use is appropriate. Review runtime checks and actual call sites.

### 6.2 Declared application components

The manifest includes:

- A launcher activity.
- A background service declared with data-sync, microphone, and camera service types.
- An accessibility service protected by the Android accessibility binding permission.
- A boot-event receiver.
- A job service protected by the job-service binding permission.

For each component, reviewers should examine:

1. Whether it must be exported.
2. Whether an appropriate permission protects it.
3. Whether external intents are validated.
4. Whether the component exposes sensitive data or actions.
5. Whether Android version-specific restrictions are respected.

### 6.3 Privacy review priorities

The highest-priority review areas are camera, microphone, accessibility, broad file
access, background execution, boot events, and remote communication. These areas
should receive explicit user-consent, least-privilege, and data-minimization checks.

## 7. Technology stack

The inspected Gradle configuration includes the following technologies and libraries.

| Technology | Role |
|---|---|
| Kotlin | Primary application language |
| Android SDK | Platform APIs |
| AndroidX AppCompat | Backward-compatible application UI support |
| AndroidX Core KTX | Kotlin extensions for Android |
| Material Components | Material UI widgets and styling |
| OkHttp | HTTP client/networking library |
| Kotlin Coroutines | Asynchronous programming |
| AndroidX CameraX | Camera framework |
| Gradle Kotlin DSL | Build configuration |
| GitHub Actions | Automated build workflow |

Dependency versions should be kept current through a deliberate compatibility and
security review. Do not upgrade multiple build-system components blindly in one change.

## 8. Build configuration

The inspected `app/build.gradle.kts` declares:

- `namespace`: `com.example.disguise`
- `applicationId`: `com.example.disguise`
- `compileSdk`: 35
- `minSdk`: 30
- `targetSdk`: 35
- `versionCode`: 1
- `versionName`: `1.0`
- Java source/target compatibility: 17
- Kotlin JVM target: 17
- View Binding: enabled

The repository README and workflow should remain the source of truth for the exact
Gradle wrapper and Android Gradle Plugin versions. Confirm these values against
`gradle/wrapper/gradle-wrapper.properties` and the root Gradle plugin configuration
before a release.

### Signing configuration

The application build file reads release-signing values from environment variables:

- `KEYSTORE_PATH`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Keep signing material outside source control. Use protected repository secrets or a
secure local environment. Never put keystore passwords, private keys, access tokens,
or bot credentials in source files, build logs, or README examples.

A build that completes without signing configuration may produce an unsigned artifact;
verify the workflow's signing and artifact-validation steps before treating any APK
as a release.

## 9. CI workflow and current build status

The repository contains a GitHub Actions workflow at:

`/.github/workflows/release-apk.yml`

The workflow is intended to automate Android release APK building. Its actual trigger,
signing behavior, artifact names, and upload conditions should be verified directly
in the workflow file whenever it changes.

### Latest inspected failure

During the previous repository inspection, the latest examined workflow run was:

- Run ID: `44751345515`
- Job ID: `136339740496`
- Commit: `6ce90fd`
- Commit message: `Update C2Service.kt`
- Result: failed during Kotlin compilation

The reported compiler diagnostics were:

1. `Unresolved reference: ActivityTracker` in `CommandHandler.kt`.
2. A type mismatch in the same file: a value of type `kotlin.Any` was supplied where
   `android.content.Context` was expected.

These are source compilation errors, not evidence of a JDK installation failure,
Gradle download failure, or keystore failure.

**Important:** The diagnostics identify the immediate reported compilation blockers.
They do not establish that no additional errors will appear after those are addressed.
The build must be run again to discover any subsequent failures.

Links:

- [GitHub Actions run](https://github.com/imaddevloper/Designs-Appm/actions/runs/44751345515)
- [Job log](https://github.com/imaddevloper/Designs-Appm/actions/runs/44751345515/job/136339740496)
- [Repository](https://github.com/imaddevloper/Designs-Appm)

## 10. Defensive analysis methodology

A responsible assessment should be repeatable, scoped, and non-invasive.

### Phase A — Source and configuration review

- Record the commit hash and date being assessed.
- Inventory Android manifest permissions and application components.
- Map sensitive APIs to their calling classes.
- Identify exported components and intent entry points.
- Review storage, logging, and secret-handling practices.
- Record findings with file paths and line references.

### Phase B — Threat modeling

For each sensitive component, document:

| Question | Expected assessment |
|---|---|
| What asset could be affected? | Device data, privacy, account security, or availability |
| What permission is involved? | Exact Android permission or platform capability |
| Is user consent explicit? | Yes/no, with evidence |
| Is the action visible? | User-facing indication and notification |
| Is access minimized? | Narrow purpose and minimum data |
| Can the user stop it? | Clear and tested disable/stop path |
| Is data transmitted? | Destination, category, and retention policy |

### Phase C — Controlled observation

If dynamic analysis is authorized, use a dedicated emulator or laboratory device with
synthetic accounts and test data. Keep the environment isolated from personal accounts
and production networks. Observe only behavior necessary to answer the approved
research question.

Do not use real personal files, private conversations, credentials, or unsuspecting
people as test data.

### Phase D — Reporting

A useful finding should include:

- Finding title and severity.
- Affected file or Android component.
- Preconditions and affected platform versions.
- Security impact.
- Evidence supporting the observation.
- Reproducible, non-invasive validation notes.
- Defensive mitigation.
- Verification status and residual risk.

## 11. Security, privacy, and laboratory requirements

Before any testing:

- Obtain written authorization and define the scope.
- Use a dedicated test device or emulator.
- Use synthetic data and test-only accounts.
- Avoid connecting the test build to personal messaging accounts.
- Do not expose the test device to untrusted networks.
- Do not collect or transmit third-party data.
- Keep credentials in a secure secret store.
- Preserve relevant logs without storing sensitive personal content.
- Provide a clear way to stop and uninstall the test application.
- Reset the test environment after the assessment.

### Recommended privacy safeguards

For any future benign research build:

- Remove unnecessary permissions.
- Replace broad storage access with scoped storage or the Storage Access Framework.
- Require a clear user action before accessing sensors.
- Show persistent, understandable status for sensitive operations.
- Avoid collecting accessibility events unrelated to the declared research task.
- Use local synthetic test data by default.
- Make network destinations explicit and configurable by the lab owner.
- Add a visible master stop control.
- Document data retention and deletion behavior.

## 12. Known limitations and verification status

This README is based on repository files and workflow information inspected at the time
of writing. It is not a full source audit, penetration test, or certification.

| Area | Current documentation status |
|---|---|
| Repository structure | Mapped from the inspected source tree |
| Manifest permissions/components | Reviewed at declaration level |
| Gradle application configuration | Reviewed |
| Main activity | Partially inspected |
| Every Kotlin implementation | Not fully audited |
| Runtime behavior | Not independently verified |
| Android-version compatibility | Not comprehensively tested |
| Network security | Requires dedicated review |
| Data handling and retention | Requires dedicated review |
| Latest inspected CI run | Failed at Kotlin compilation |

Do not describe the project as safe, production-ready, or fully functional until these
items have been tested and documented.

## 13. Responsible-use policy

By using or modifying this repository, researchers should:

1. Work only on devices and systems they own or are explicitly authorized to assess.
2. Respect privacy, consent, and applicable laws.
3. Avoid covert monitoring or unauthorized data collection.
4. Avoid deploying the application to unsuspecting users.
5. Keep test infrastructure isolated and credentials protected.
6. Report vulnerabilities responsibly.
7. Stop testing if activity exceeds the approved scope.

The author and contributors should not publish secrets, private data, or instructions
that enable unauthorized access through this repository.

## 14. License and attribution

No license grant should be assumed unless a valid license file is present in the
repository. Before redistribution, modification, or publication, verify the repository's
license and the licenses of all third-party dependencies.

---

<p align="center">
  <sub>DisguiseApp · Android Security Research · Documentation</sub>
</p>
