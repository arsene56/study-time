param(
    [string]$BaseUrl = "http://127.0.0.1:8080"
)

$ErrorActionPreference = "Stop"

Write-Host "1/6 检查后端健康状态"
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health"
if ($health.status -ne "UP") {
    throw "后端健康状态不是 UP"
}

Write-Host "2/6 读取演示家庭"
$context = Invoke-RestMethod -Uri "$BaseUrl/api/v1/demo/context"
$child = $context.children | Select-Object -First 1
if (-not $child) {
    throw "演示家庭中没有孩子"
}

Write-Host "3/6 模拟识别 $($child.name) 的作业"
$batch = Invoke-RestMethod `
    -Method Post `
    -Uri "$BaseUrl/api/v1/homework-batches/mock-recognize?childId=$($child.id)"
if ($batch.status -ne "PENDING_CONFIRMATION" -or $batch.tasks.Count -eq 0) {
    throw "模拟识别没有返回待确认任务"
}

Write-Host "4/6 确认并生成计划"
$plan = Invoke-RestMethod `
    -Method Post `
    -Uri "$BaseUrl/api/v1/homework-batches/$($batch.id)/confirm-and-plan" `
    -ContentType "application/json" `
    -Body '{"startTime":"17:30"}'
$task = $plan.items | Where-Object { $_.kind -eq "HOMEWORK" } | Select-Object -First 1
if (-not $task) {
    throw "生成的计划中没有作业任务"
}

Write-Host "5/6 以孩子身份完成首项任务"
$actor = @{
    actorId = $child.id -replace "demo-child-", "demo-child-member-"
    actorName = $child.name
    actorRelation = "孩子"
} | ConvertTo-Json
$updatedPlan = Invoke-RestMethod `
    -Method Post `
    -Uri "$BaseUrl/api/v1/plan-items/$($task.id)/complete" `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($actor))
$completed = $updatedPlan.items | Where-Object { $_.id -eq $task.id }
if ($completed.status -ne "DONE") {
    throw "任务完成状态没有写回"
}

Write-Host "6/6 检查操作记录"
$activities = Invoke-RestMethod -Uri "$BaseUrl/api/v1/children/$($child.id)/activities"
if (-not ($activities | Where-Object { $_.actionType -eq "TASK_COMPLETED" })) {
    throw "没有找到任务完成操作记录"
}

Write-Host "最小纵向闭环验证通过：家长录入 → 生成计划 → 孩子完成 → 操作记录写回。" -ForegroundColor Green
