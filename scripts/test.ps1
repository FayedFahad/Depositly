. (Join-Path $PSScriptRoot 'common.ps1')
& (Join-Path $ProjectRoot 'tests/ci-version.test.ps1')
& (Join-Path $ProjectRoot 'tests/version-bump.test.ps1')
foreach ($test in @('dates.test.cjs','reminders.test.cjs','renewal.test.cjs','payout-rules.test.cjs','interest-backup.test.cjs','payout-migration.test.cjs','investment-form.test.cjs','compound-display.test.cjs','forecast-charts.test.cjs','home-forecast.test.cjs','startup-branding.test.cjs','receipt-reconciliation.test.cjs','cloud.test.cjs','registry.test.cjs','deposit-view.test.cjs','bank-backup.test.cjs','bank-information.test.cjs','bank-layout.test.cjs','deposit-account-data.test.cjs','deposit-account-registry.test.cjs','deposit-account-ui.test.cjs','payment-account-ui.test.cjs')) {
    & node (Join-Path $ProjectRoot "tests/$test") | Out-Host
    Assert-ToolExit
}
$testPath = Join-Path (Get-AppWorkDirectory) 'policy-tests'
New-Item -ItemType Directory -Force -Path $testPath | Out-Null
& javac --release 8 -d $testPath (Join-Path $ProjectRoot 'native/src/app/depositly/mobile/reminders/ReminderPolicy.java') (Join-Path $ProjectRoot 'tests/ReminderPolicyTest.java') | Out-Host
Assert-ToolExit
& java -cp $testPath ReminderPolicyTest | Out-Host
Assert-ToolExit

