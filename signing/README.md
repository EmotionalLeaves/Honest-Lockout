# Signing

`honest-public.jks` is a **non-secret** keystore committed so that GitHub Actions can sign
release APKs before the repository secrets exist.

| Field | Value |
| --- | --- |
| Store type | PKCS12 |
| Store password | `honestlockout` |
| Key alias | `honest` |
| Key password | `honestlockout` |

Android only installs an update over the existing app if it is signed with the **same key**.
So when you add the four repository secrets later, upload **this** keystore, not a new one:

1. In Termux: download `signing/honest-public.jks` from the repo, then
   `base64 -w 0 honest-public.jks > honest.b64` and copy the contents.
2. Repo Settings > Secrets and variables > Actions, add:
   - `SIGNING_KEY_BASE64` = contents of `honest.b64`
   - `KEYSTORE_PASSWORD` = `honestlockout`
   - `KEY_ALIAS` = `honest`
   - `KEY_PASSWORD` = `honestlockout`

If you instead generate a brand-new key, the first APK signed with it will not install over
the old one: you would have to uninstall, which deletes all strikes, history and pending changes.
