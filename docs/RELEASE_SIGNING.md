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

Older CI APKs were signed with runner-generated debug keys. Those private keys are not recoverable from the APK. A new stable key therefore cannot update those installations in place. Install v1.0.15 once after uninstalling the older debug-signed build; after the stable signing secrets are configured, later releases can update over that v1.0.15 installation without uninstalling.
