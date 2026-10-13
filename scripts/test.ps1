. (Join-Path $PSScriptRoot 'common.ps1')
foreach ($test in @('dates.test.cjs','reminders.test.cjs','renewal.test.cjs')) {
    & node (Join-Path $ProjectRoot "tests/$test") | Out-Host
    Assert-ToolExit
}
$testPath = Join-Path $ProjectRoot 'build/policy-tests'
New-Item -ItemType Directory -Force -Path $testPath | Out-Null
& javac --release 8 -d $testPath (Join-Path $ProjectRoot 'native/src/app/depositly/mobile/reminders/ReminderPolicy.java') (Join-Path $ProjectRoot 'tests/ReminderPolicyTest.java') | Out-Host
Assert-ToolExit
& java -cp $testPath ReminderPolicyTest | Out-Host
Assert-ToolExit

