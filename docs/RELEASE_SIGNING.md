# Aetheris Android release signing

Android only accepts an in-place APK update when the package ID is unchanged, the new version code is not lower, and the signing certificate matches the installed app (unless a supported signing-key rotation is configured).

## Configure stable CI signing

Create a private release keystore on a trusted machine and keep a secure offline backup:

```bash
keytool -genkeypair -v -keystore aetheris-release.jks -alias aetheris \
  -keyalg RSA -keysize 3072 -validity 10000
base64 -w0 aetheris-release.jks
```

Add these GitHub repository Actions secrets under Settings → Secrets and variables → Actions:

- `AETHERIS_KEYSTORE_BASE64`: the base64 output above
- `AETHERIS_KEYSTORE_PASSWORD`: the keystore password
- `AETHERIS_KEY_ALIAS`: `aetheris` (or the alias chosen above)
- `AETHERIS_KEY_PASSWORD`: the private-key password
- `RELEASE_TOKEN`: a token permitted to create/update releases if the repository's default Actions token cannot publish releases

Never commit the keystore, passwords, or private key to the repository.

## Existing installations

Older CI APKs were signed with runner-generated debug keys. Those private keys are not recoverable from the APK. A new stable key therefore cannot update those installations in place. The v1.0.15 CI log confirmed that `AETHERIS_KEYSTORE_BASE64` was not configured, so it was signed with a temporary runner debug key. v1.0.16 uses the same fallback unless you add the secrets before the release workflow builds it. You cannot make an already-installed APK accept a different signing certificate.

For a durable update path:

1. Add all four `AETHERIS_*` signing secrets above before building the release you intend to keep installed.
2. Run the v1.0.16 release workflow again so the release APK is signed with that stable key.
3. Uninstall the older debug-signed Aetheris once, then install the stable-key v1.0.16 APK. Uninstalling erases local app data, so back up Notebook games/PGNs first.
4. Keep the keystore and passwords backed up securely. All later releases signed with the same key and a higher version code can update in place.

The app's updater now checks the APK's certificate before launching Android's installer and reports a clear mismatch message. Ordinary Android apps cannot silently install APKs: Android still requires the system installer confirmation. “Check for Updates” stages the APK privately and opens that installer; “Download Latest APK to Downloads” only saves the file.
