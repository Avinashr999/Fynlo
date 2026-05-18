# Privacy Policy for Fynlo

**Last Updated: May 16, 2026**

This Privacy Policy describes how **Fynlo** ("the App", "we", "us", or "our") handles your information when you use our personal finance management application.

## 1. Overview

Fynlo is a personal finance tracker that helps you manage loans, expenses, investments, and debts. Your financial data belongs to you. We do not sell, share, or monetize your data.

## 2. Data You Provide

- **Financial records**: Transactions, loans, debts, investments, accounts, budgets, and goals you manually enter
- **Display name** (optional): Used only for in-app greetings
- **Preferences**: Language, theme, currency, and date format settings

## 3. Data Collected Automatically

- **Crash reports**: Anonymous crash and ANR data via Firebase Crashlytics (device model, OS version, stack traces)
- **Performance metrics**: App startup time and screen load times (anonymous)
- **Basic analytics**: Screen views and feature usage patterns (no financial data is included)

### Data We Do NOT Collect
- Financial transaction amounts, account balances, or personal financial details are never sent to analytics
- We do not access your contacts, SMS, calls, camera, or location
- We do not display ads or sell data to third parties
- We do not track you across other apps or websites

## 4. Data Storage

### Local Storage (On-Device)
All financial data is stored locally using a Room database. PIN credentials use Android Keystore. Preferences use Jetpack DataStore. This data never leaves your device unless you sign in for cloud sync.

### Cloud Sync (Optional, Requires Google Sign-In)
If you sign in with Google, your data syncs to Firebase Firestore for backup and multi-device access. Your data is stored in a personal, isolated document — security rules prevent any other user from accessing it. You can use the app entirely offline without signing in.

### Exports
JSON, CSV, Excel, and PDF exports are saved to a location you choose on your device. You are responsible for these files once exported.

## 5. Data Security

- All network communication uses HTTPS (cleartext HTTP is disabled)
- Firebase Firestore enforces per-user data isolation
- Release builds strip all debug logs
- PIN lock and biometric authentication protect app access
- No API keys or secrets are stored in application source code

## 6. Third-Party Services

| Service | Purpose | Privacy Policy |
|---------|---------|----------------|
| Firebase Authentication | Google Sign-In | [Google Privacy](https://policies.google.com/privacy) |
| Firebase Firestore | Cloud sync & backup | [Google Privacy](https://policies.google.com/privacy) |
| Firebase Crashlytics | Crash reporting | [Google Privacy](https://policies.google.com/privacy) |
| Firebase Analytics | Anonymous usage patterns | [Google Privacy](https://policies.google.com/privacy) |
| Firebase Performance | App speed monitoring | [Google Privacy](https://policies.google.com/privacy) |

## 7. Your Rights

- **Export**: Download all your data anytime (JSON, CSV, Excel, PDF)
- **Delete**: Wipe all local and cloud data from Settings
- **Opt out of analytics**: Disable via your device's Google Ads settings

## 8. Children's Privacy

Fynlo is not intended for children under 13. We do not knowingly collect data from children.

## 9. Changes to This Policy

We may update this policy when adding new features. Continued use after changes constitutes acceptance.

## 10. Contact Us

For privacy questions or data requests:
- Email: avinash999.reddy@gmail.com
- WhatsApp: +91 8500504810
