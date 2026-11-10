---
type: source
title: Android app cannot read /proc/stat on CPH2717
status: insight
category: android
created: 2026-11-10
updated: 2026-11-10
slug: android-proc-stat-selinux-inference-metrics
---

# Android app cannot read /proc/stat on CPH2717

On the LocalGlasses CPH2717 (Android 16), the app UID gets `Permission denied` for `/proc/stat`, so whole-device CPU percentage is unavailable. `adb shell` can read it, but `adb shell run-as com.localglasses.app cat /proc/stat` cannot. Core frequency sysfs (`/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq`) is readable, and `Process.getElapsedCpuTime()` works for app CPU. The stats UI correctly renders `Unavailable` instead of zero. See [[sources/obs-2026-11-10-localglasses-stats-for-nerds-design-settled]].

## Fix / handling (verified 2026-11-11)

There is no app-side permission that makes `/proc/stat` readable. Keep whole-device CPU as
`Unavailable`; do not root the phone or fabricate a percentage. App CPU still works through
`Process.getElapsedCpuTime()`, and per-core frequency is readable from
`/sys/devices/system/cpu/cpuN/cpufreq/scaling_cur_freq`. The Stats UI showed `Device CPU:
Unavailable` while `App CPU` updated normally.

*Category: android*

---
*Captured: 2026-11-10*

## Related

_Add links to related pages._
