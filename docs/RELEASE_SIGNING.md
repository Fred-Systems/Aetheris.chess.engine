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

Earlier CI builds may have been signed with temporary runner-generated debug keys if the stable keystore secret was absent. Those private keys are not recoverable from the APK. A stable-key APK cannot update an installation signed with a different certificate; Android will reject the update.

The v1.0.18 release workflow is configured to **fail rather than publish a debug-signed APK** when any required signing secret is missing. Before running it, confirm that all four `AETHERIS_*` secrets above are present and correct. The workflow decodes the keystore into the temporary runner directory, passes the passwords and alias to Gradle through environment variables, and never prints their values.

If the installed app was signed with a previous temporary key, back up Notebook games/PGNs, uninstall that old app once, and install the stable-key APK. Subsequent releases signed with the same key and a higher version code can update in place.

The app's updater checks the APK's certificate before launching Android's installer and reports a mismatch message. Ordinary Android apps cannot silently install APKs; Android still requires the system installer confirmation. “Check for Updates” stages the APK privately and opens that installer; “Download Latest APK to Downloads” only saves the file.
