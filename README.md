# SnapMapper

**Give the Snap Key on your OPPO phone a new job.** Single, double and long press, each with
its own action. No root, no internet, no permissions to grant.

<p align="center">
  <img src="docs/screenshot.png" width="320" alt="SnapMapper settings screen">
</p>

- **Open source**, MIT licensed.
- **Tiny: 96 KB.** The whole app (APK) is 97,893 bytes.
- **No extra battery use.** Nothing runs on a timer, it never polls, and it has no internet
  access. It only does work when you press the Snap Key.
- **No permissions to grant.** The only thing you switch on is its accessibility service.

---

## Contents

- [What it can do](#what-it-can-do)
- [Which phones](#which-phones)
- [Install](#install)
- [Choosing actions (read this: long press)](#choosing-actions)
- [Permissions and privacy](#permissions-and-privacy)
- [Size and battery](#size-and-battery)
- [How it works](#how-it-works)
- [Limitations](#limitations)
- [Uninstall](#uninstall)
- [Build from source](#build-from-source)
- [Credits and license](#credits-and-license)

---

## What it can do

**Three gestures:** single press, double press and long press, each with its own action.

**Actions**

| | |
|---|---|
| Toggle flashlight | Switch ring / vibrate |
| Cycle ring / vibrate / silent | Take screenshot |
| Open camera / record video | Open any app |
| Open a link | Play / pause, next, previous track |
| Voice assistant | Notifications, quick settings, recent apps |
| Home, back, lock screen, power menu | Split screen |
| Start a timer | Web search |

**Smart behaviour**

- **Different actions when locked.** Choose a separate set for when the screen is off or locked.
- **Per-app actions.** Replace the single press while a chosen app is open, for example a
  shutter or play/pause in a specific app.
- **Pause during calls.** The Snap Key is ignored while you're on a call.
- **Feel the result.** Switching to vibrate gives one long strong buzz; switching to ring gives
  two short strong buzzes. You know what happened without looking.
- **On-screen confirmation (optional).** A small pill around the front camera shows what ran.
  Its position can be fine-tuned in Advanced settings.
- **Advanced settings.** Exact double-press window in milliseconds and a press history.

The settings screen follows the ColorOS look: large title, rounded cards, light and dark mode.

---

## Which phones

Tested on the **OPPO Find X9 Ultra** (CPH2841, ColorOS 17, Android 17).

It should work on other OPPO / OnePlus phones with a Snap Key (also called the action button),
**as long as the ColorOS Translate app (`com.coloros.translate`) can be removed or isn't
installed**. See [How it works](#how-it-works) for why.

---

## Install

You need a computer with [ADB](https://developer.android.com/tools/releases/platform-tools)
**once**, for the install. After that you never need the computer or Developer options again.

### 1. Remove ColorOS Translate (if you have it)

SnapMapper takes the place of the ColorOS Translate app, so Translate can't be installed at
the same time.

- Open **Settings → Apps → App management**, find **Translate**, and tap **Uninstall**.
- If you don't see Translate, you don't have it. Skip this step.
- If Translate is a system app that can't be uninstalled on your phone, SnapMapper can't be
  installed there.

### 2. Turn on USB debugging (temporarily)

1. **Settings → About device → Version**, tap **Build number** 7 times.
2. **Settings → Additional settings → Developer options**, turn on **USB debugging**.
3. Connect the phone to your computer and tap **Allow** on the phone.

### 3. Install SnapMapper

Download `SnapMapper.apk` from the [latest release](../../releases/latest), then run:

```bash
adb install -i com.android.vending SnapMapper.apk
```

> **Why `-i com.android.vending`?** ColorOS refuses to install apps that use an OPPO-style
> package name (like `com.coloros.translate`) unless they come from an app store. This flag
> tells Android the install comes from Google Play, which ColorOS accepts. Installing the APK by
> tapping it in a file manager will fail with "app not installed" for this reason.

### 4. Turn Developer options off again

**Settings → Additional settings → Developer options**, switch it off. SnapMapper doesn't
need it.

### 5. Turn on SnapMapper

1. Open **SnapMapper**.
2. Tap **Turn on SnapMapper** → **Downloaded apps → SnapMapper → On**.
3. If the switch is greyed out: **Settings → Apps → SnapMapper → ⋮ (top right) → Allow
   restricted settings**, then try again. (Android does this for every app installed outside
   an app store.)

### 6. Point the Snap Key at SnapMapper

**Settings → Additional settings → Snap Key → Translate.**

SnapMapper shows **"Snap Key is ready"**. Press the Snap Key to try it, then pick your actions.

---

## Choosing actions

**Use single and double press for anything you do while typing. Use long press for actions
that open something.**

On a **single or double press**, ColorOS sends SnapMapper a silent background message.
Nothing on screen changes.

On a **long press**, ColorOS always opens a tiny invisible SnapMapper screen. This is how
ColorOS handles long presses for the Translate slot, and SnapMapper can't change it. That
screen closes instantly, but if the keyboard is open, it **briefly hides and reappears
(flickers)**.

| Good for long press | Better on single / double press |
|---|---|
| Open camera / record video | Switch ring / vibrate |
| Open an app or a link | Cycle ring / vibrate / silent |
| Take screenshot | Toggle flashlight |
| Voice assistant | Play / pause, next / previous track |
| Lock screen, power menu, recent apps | Anything you use while typing |
| Start a timer, web search | |

**Screen off:** ColorOS turns the screen on for every long press. For actions that don't need
the screen (ring/vibrate, flashlight, media), SnapMapper turns it straight back off, so you'll
see at most a brief flash.

Double press: SnapMapper waits a moment after the first press to see if a second one follows.
If no double-press action is set, single presses run instantly. You can tune the wait under
**Behaviour → Double press speed**, or set it exactly in **Advanced settings**.

---

## Permissions and privacy

SnapMapper asks for **no runtime permissions**. There's nothing to allow in a pop-up.

| | |
|---|---|
| Internet | **No.** The app has no internet permission, so nothing can leave your phone. |
| Storage, location, contacts, camera, mic | **No.** |
| Root | **No.** |
| ADB / Developer options | **Only once, to install.** Not used at runtime. |
| Vibration | Yes, for haptic feedback. Android grants it automatically. |
| Accessibility service | **The one thing you switch on.** See below. |

**What the accessibility service is used for:** only for things a normal app isn't allowed to
do from the background: taking a screenshot, locking the screen, opening notifications, quick
settings, recent apps or the power menu, opening apps while you're in another app, and showing
the optional pill around the camera. It's configured to **not read screen content**
(`canRetrieveWindowContent="false"`) and doesn't receive your key presses or typing. If you add
per-app actions, it's told which app is in the foreground, and only then.

Settings and press history are stored only on the phone.

---

## Size and battery

**Lightweight:**

| | |
|---|---|
| Download (APK) | **96 KB** (97,893 bytes) |
| Installed size | **96 KB** app file (Android may add a little compiled code and cache) |
| Libraries | None. Plain Android framework code, no AndroidX, no third-party SDKs |
| Network access | None (no internet permission) |

**No extra battery use:**

- **No timers, no polling, no wake locks, no location, no network.**
- **Idle between presses.** Android keeps its accessibility service loaded, as it does for
  every accessibility app, but the service sits idle and does no work.
- **It works only when you press.** ColorOS wakes SnapMapper's small press receiver when you
  press the Snap Key, and disconnects it about a minute later.
- **No key filtering.** It doesn't inspect other buttons or your typing.
- **The optional camera pill** uses one small window that's hidden, and draws nothing, between
  presses.

To check on your own phone: **Settings → Battery → Battery usage**. SnapMapper (it may be
listed as Translate) should be at or near 0%.

---

## How it works

ColorOS doesn't let normal apps see the Snap Key. The button
(`BTN_TRIGGER_HAPPY32` on the `gpio-keys` input device) isn't mapped to an Android key code,
so accessibility services and key listeners never receive it. ColorOS handles it inside the
system instead:

1. The framework (`StrategyActionButtonKeyLaunchApp`) reads the raw key and turns it into
   internal events: **single tap** or **long press**.
2. ColorOS's gesture app (`com.oplus.gesture`) runs the function you chose in
   **Settings → Snap Key**. Each function ("scene") is defined in its built-in
   `action_button_scene_config.xml`, which names an **owner package** and what to start.
3. For **Translate**, the owner is `com.coloros.translate`, and:
   - **single tap**: the gesture app binds to `com.coloros.translate/.ui.cube.KeyMessengerService`
     and sends a Messenger message with `key = "SINGLE_TAP"`;
   - **long press**: it starts the activity action `coloros.intent.action.translate.CUBE`
     in that package, with `action_key_type = 2`.

SnapMapper uses the package name `com.coloros.translate` and provides exactly that service and
activity, so ColorOS delivers every Snap Key press to it. SnapMapper only accepts messages sent
by `com.oplus.gesture`. It then works out single / double / long press and runs your action.

Other routes that were tried and don't work without OPPO's signature or root:
ColorOS's key interceptor API (`OplusKeyEventManager`) and the Snap Key scene database both
require the signature-only permission `oplus.permission.OPLUS_COMPONENT_SAFE`. The hidden
"test" Snap Key function (service-only long press) is removed from the list by ColorOS on every
boot.

---

## Limitations

- **Snap Key must stay on "Translate".** Any other function sends presses back to ColorOS.
- **Long press flickers the keyboard** if it's open (see [Choosing actions](#choosing-actions)).
- **If OPPO installs the real Translate app**, for example through a system update, it will
  conflict with SnapMapper and the Snap Key will stop reaching it. Uninstall Translate again
  and reinstall SnapMapper.
- **A ColorOS update could change how the Snap Key works.** It works on the version above;
  other versions are untested.
- In ColorOS menus that list the Snap Key function, SnapMapper may appear as "Translate".
- Updates must be signed with the same key as the installed version. If you build it yourself,
  uninstall the release version first.

---

## Uninstall

1. **Settings → Apps → SnapMapper → Uninstall.**
2. **Settings → Additional settings → Snap Key**: choose any other function.

Nothing else was changed on your phone.

---

## Build from source

Requirements: JDK 17 and the Android SDK (platform 35).

```bash
git clone https://github.com/amarsakore/SnapMapper.git
cd SnapMapper
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. Install it with the
command from [step 3](#3-install-snapmapper).

Project layout:

| File | What it does |
|---|---|
| `app/src/main/java/com/coloros/translate/ui/cube/KeyMessengerService.kt` | Receives single taps from ColorOS |
| `app/src/main/java/com/coloros/translate/ui/cube/LongPressActivity.kt` | Receives long presses |
| `app/src/main/java/com/amar/snapmapper/SnapKeyService.kt` | Accessibility service: gestures, locked/per-app logic, camera pill |
| `app/src/main/java/com/amar/snapmapper/KeyAction.kt` | All actions and their haptics |
| `app/src/main/java/com/amar/snapmapper/MainActivity.kt` | The settings screen |
| `app/src/main/java/com/amar/snapmapper/Prefs.kt` | Settings storage |

---

## Credits and license

Inspired by [KeySnap](https://github.com/Nicholas-Arda/KeySnap). No code from KeySnap is used.

SnapMapper isn't affiliated with or endorsed by OPPO. "OPPO", "ColorOS" and "Snap Key" are
trademarks of their owners.

Released under the [MIT License](LICENSE).
