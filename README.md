# Ring Through

An Android app that lets chosen callers ring through silent mode and Do Not Disturb.

Many people keep their phone on silent, and then miss the one call that matters. Ring Through
gives you a short list of people who can always reach you.

## How it works

1. A call comes in. The app compares the number with your list.
2. If the caller is on the list and the phone is silent, the app waits for a short delay.
3. If the call is still ringing, the app turns Do Not Disturb off, sets the ringer to normal and
   plays the ringtone.
4. When the call ends, the app puts your earlier sound settings back.

Calls from other numbers are not changed. If the phone can already be heard, the app does nothing.

## Design notes

- The app has no account, no server and no network code. Your list stays on the phone.
- The work runs in a `BroadcastReceiver` with `goAsync()`, which is held for at most 55 seconds
  to stay inside the 60-second background broadcast limit.
- Android sends the ringing broadcast twice, once without the number and once with it. The app
  acts only on the broadcast that has the number, so it needs `READ_CALL_LOG`.

## Build

Requires the Android SDK and JDK 21. Gradle fails on JDK 27. Supports Android 10 (API 29) and later.

```sh
./gradlew assembleDebug
```

After you install the app, open it and grant the permissions it asks for, including Do Not
Disturb access and the battery optimization exemption.

## License

MIT
