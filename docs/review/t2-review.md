# T2 Review — Simplified Chinese Support

**Branch**: `T2-simplified-chinese-support`  
**Commits reviewed**: 25cb103..ca37d0f (5 commits)  
**Scope**: 33 files, +1642 / −536

## Plan Adherence

The implementation follows the plan in `docs/todo/in-progress.md`:

- [x] Extract ~470 UI strings into `values/strings.xml`
- [x] Create `values-zh-rCN/strings.xml` with Chinese translations
- [x] Refactor 20+ Compose files to use `stringResource(R.string.*)`
- [x] Change `Screen.label: String` → `Screen.labelResId: Int` for localized drawer labels
- [x] Add `LocaleHelper.kt` with runtime locale switching
- [x] Add language picker in Settings
- [x] Update `resConfigs("en", "zh-rCN")` in `build.gradle.kts`
- [x] Build succeeds

## Issues

### BUG (moderate) — `sessions_saved_auto` translation incomplete

**File**: `app/src/main/res/values-zh-rCN/strings.xml:235`

The English string was updated in commit 9909ac2 to read:

> "Sessions are saved automatically when you plug or unplug the charger, **and only when the battery percentage actually changes**."

But the Chinese translation was not updated to match:

```
插入或拔下充电器时会自动保存会话。
```

Missing clause: `并且仅在电池百分比实际变化时`.

### MINOR — Remaining hardcoded strings

The first commit acknowledged "remaining hardcoded strings can be migrated in follow-up items." These are still present:

| File | Line | String |
|------|------|--------|
| `ThermalCard.kt` | 76 | `"Peak: ${status.maxTemp}°C"` |
| `ThermalCard.kt` | 82 | `" • Limit: ${status.currentLimit}°C"` |
| `ThermalCard.kt` | 130 | `"${zone.temp}°C"` |
| `ThermalCard.kt` | 192 | `"${sliderValue.toInt()}°C"` |
| `UfsComponents.kt` | 107 | `"Current: ${ufs.currentFreq}"` |
| `BatteryHistoryComponents.kt` | 103 | `signedChange + "%"` |
| `BatteryHistoryComponents.kt` | 109 | `"...% → ...%"` |
| `BatteryScreen.kt` | 167 | `"${stats.percent}%"` |
| `BatteryScreen.kt` | 488 | `"+${stats.chargingGainedPercent}%"` |
| `BatteryScreen.kt` | 499 | `"...% → ...%"` |
| `RamScreen.kt` | 205 | `"${memory.usedUi} / ${memory.totalUi}"` |
| `RamScreen.kt` | 273 | `"${swap.usedUi} / ${swap.totalUi}"` |
| `PowerComponents.kt` | 101 | `"W"` |
| `PowerComponents.kt` | 282 | Axis labels |

### INFO — `BootToggleCard` default params

`CommonComponents.kt:316` uses `stringResource()` as default parameter values in a `@Composable` function. This is valid — the Compose compiler generates `$default` synthetic methods for this.

## Translation Quality

- Chinese translations read naturally with correct terminology (调度器 for governor, 覆盖层 for overlay, 悬浮窗 for floating overlay)
- Format specifiers (`%1$s`, `%1$d`, etc.) match between English and Chinese
- String count parity: both files are 486 lines with matching resource IDs
- The language picker correctly shows "简体中文" as the label

## Verdict

**CHANGES_REQUESTED** — fix the `sessions_saved_auto` Chinese translation gap before merging.
