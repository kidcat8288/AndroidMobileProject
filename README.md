# MoneyMate

MoneyMate is a Kotlin and Jetpack Compose personal-finance app.

Open this folder in Android Studio, let Gradle sync, and run the `app` configuration on an Android 8.0+ device or emulator.

Features: balance dashboard, locally stored transactions, search/filter activity history, spending reports, and a monthly budget estimator.

## UI-only prototype

Open `app/src/main/java/com/moneymate/app/prototype/MoneyMatePrototype.kt` in Android Studio and select **Split** or **Design** to see five Compose previews: Home, Activity, Reports, Budget, and receipt review. The prototype uses sample transactions and local UI state only. In Interactive Mode, tap an Activity row to edit an expense or salary income, or confirm deletion of an expense; its Home and Reports totals update on screen. These prototype changes reset when the preview restarts. It does not call the camera or OCR, save transactions, or alter the running app.

In the installed app, tap an Activity row to edit its description, amount, or category. Expenses also offer **Delete expense** with confirmation. Salary income can be edited but not deleted from this screen. Changes are saved to the existing transaction record and reflected in the dashboard and reports.

Reports can show the last 7 days, last 14 days, or last 30 days, including today. Tap **Custom range** to pick exact start and end dates; both dates are included. The selected range filters expense totals and category breakdowns in the app and prototype.

## Scan a receipt

Tap the camera button above **+** on Home or Activity. Take a clear photo using your phone's camera and confirm it. MoneyMate uses the bundled Google ML Kit Latin text recognizer on-device, including offline, then opens an editable expense draft. Review the merchant and total, choose a category, and tap **Save**. No transaction is created when you cancel.

The first version targets English receipts in USD. The amount must appear on the same OCR row as a **Total** label (case-insensitive, including **Grand Total**). Subtotals, tax, amounts on the next row, and standalone **Amount Due** or **Balance Due** labels are ignored. Missing or conflicting totals remain blank for manual entry. Expand **Show receipt text** to check the recognized text. Receipt photos are temporary and are not attached to saved transactions. Review text is limited to 32,000 characters to keep Android saved state bounded.

Scanner implementation: `app/src/main/java/com/moneymate/app/receipt/ReceiptScanner.kt`. The camera is delegated to an installed camera app; MoneyMate does not require direct camera or storage permission.

## Verify

Run `./gradlew :app:testDebugUnitTest :app:assembleDebug` (use `gradlew.bat` on Windows). Parser tests cover totals, missing fields, and ambiguous receipts.

Device tests in `ReceiptScannerDeviceTest` use a generated receipt image and the real ML Kit recognizer. They cover captured-image state restoration, review edits, saving once, cancellation, failed recognition, manual entry, and expandable receipt text. They use separate test preferences and do not modify your transaction history. Run them from Android Studio on a device or emulator. Native camera launch and real photo quality should also be checked manually.
