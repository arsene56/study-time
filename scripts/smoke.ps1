param([string]$BaseUrl = "http://127.0.0.1:8080")

$ErrorActionPreference = "Stop"
$ApiUrl = "$BaseUrl/api/v1"

function ConvertTo-Utf8JsonBytes($Value) {
    $json = $Value | ConvertTo-Json -Depth 8 -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    Write-Output -NoEnumerate $bytes
}

Write-Host "1/15 检查后端健康状态与 OCR 能力"
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health"
if ($health.status -ne "UP") { throw "后端健康状态不是 UP" }
$capability = Invoke-RestMethod -Uri "$ApiUrl/recognition-capabilities"
if (-not $capability.configuredProvider -or -not $capability.message) { throw "OCR 能力状态不完整" }

Write-Host "2/15 读取演示家庭并模拟识别"
$context = Invoke-RestMethod -Uri "$ApiUrl/demo/context"
$child = $context.children | Select-Object -Last 1
if (-not $child) { throw "演示家庭中没有孩子" }
$batch = Invoke-RestMethod -Method Post -Uri "$ApiUrl/homework-batches/mock-recognize?childId=$($child.id)"
if ($batch.status -ne "PENDING_CONFIRMATION" -or $batch.tasks.Count -eq 0) { throw "模拟识别没有返回待确认任务" }
if (-not $batch.ocrProvider -or -not $batch.ocrRawText) { throw "识别批次缺少 OCR 元数据" }
if (-not ($batch.tasks | Where-Object { $_.estimateSource -in @("GRADE_DEFAULT", "PERSONAL_HISTORY", "SUBJECT_HISTORY") })) { throw "识别结果缺少耗时估算来源" }

Write-Host "3/15 验证人工补录、修订与删除"
$manual = @{ subject = "其他"; title = "自动验收补录"; taskType = "书写"; estimatedMinutes = 15; difficulty = "MODERATE"; eyeLoad = "HIGH" }
$batch = Invoke-RestMethod -Method Post -Uri "$ApiUrl/homework-batches/$($batch.id)/tasks" -ContentType "application/json; charset=utf-8" -Body (ConvertTo-Utf8JsonBytes $manual)
$manualTask = $batch.tasks | Where-Object title -eq $manual.title | Select-Object -First 1
if (-not $manualTask -or $manualTask.estimateSource -ne "MANUAL_OVERRIDE") { throw "人工补录没有生效" }
$manual.title = "自动验收已修订"
$batch = Invoke-RestMethod -Method Put -Uri "$ApiUrl/homework-tasks/$($manualTask.id)" -ContentType "application/json; charset=utf-8" -Body (ConvertTo-Utf8JsonBytes $manual)
if (-not ($batch.tasks | Where-Object title -eq $manual.title)) { throw "人工修订没有生效" }
$batch = Invoke-RestMethod -Method Delete -Uri "$ApiUrl/homework-tasks/$($manualTask.id)"
if ($batch.tasks | Where-Object id -eq $manualTask.id) { throw "人工删除没有生效" }

Write-Host "4/15 确认并生成计划"
$plan = Invoke-RestMethod -Method Post -Uri "$ApiUrl/homework-batches/$($batch.id)/confirm-and-plan" -ContentType "application/json; charset=utf-8" -Body '{"startTime":"17:30"}'
$firstTask = $plan.items | Where-Object { $_.kind -eq "HOMEWORK" } | Select-Object -First 1
if (-not $firstTask -or $plan.bedtimeBufferMinutes -lt 30) { throw "计划生成或睡前预留不正确" }
$actor = @{
    actorId = $child.id -replace "demo-child-", "demo-child-member-"
    actorName = $child.name
    actorRelation = "孩子"
}

Write-Host "5/15 开始任务并选择继续挑战"
$started = Invoke-RestMethod -Method Post -Uri "$ApiUrl/plan-items/$($firstTask.id)/start" -ContentType "application/json; charset=utf-8" -Body (ConvertTo-Utf8JsonBytes $actor)
if (($started.items | Where-Object id -eq $firstTask.id).status -ne "ACTIVE") { throw "任务没有进入进行中状态" }
$continueRequest = $actor + @{ decision = "CONTINUE"; actualSeconds = 120; extraMinutes = 10 }
$continued = Invoke-RestMethod -Method Post -Uri "$ApiUrl/plan-items/$($firstTask.id)/overrun-decision" -ContentType "application/json; charset=utf-8" -Body (ConvertTo-Utf8JsonBytes $continueRequest)
if (($continued.items | Where-Object id -eq $firstTask.id).overrunDecision -ne "CONTINUE") { throw "继续挑战选择没有写回" }

Write-Host "6/15 完成首项并动态更新剩余计划"
$completeRequest = $actor + @{ actualSeconds = 180 }
$completed = Invoke-RestMethod -Method Post -Uri "$ApiUrl/plan-items/$($firstTask.id)/complete" -ContentType "application/json; charset=utf-8" -Body (ConvertTo-Utf8JsonBytes $completeRequest)
if (($completed.items | Where-Object id -eq $firstTask.id).status -ne "DONE") { throw "任务完成状态没有写回" }

Write-Host "7/15 检查孩子个性化画像"
$profile = Invoke-RestMethod -Uri "$ApiUrl/children/$($child.id)/personalization-profile"
if ($profile.childId -ne $child.id -or -not $profile.confidence -or $profile.totalSamples -lt 1) { throw "个性化画像没有吸收完成样本" }

Write-Host "8/15 以家长身份调整任务顺序"
$ids = [System.Collections.Generic.List[string]]::new()
$completed.items.id | ForEach-Object { $ids.Add($_) }
if ($ids.Count -gt 2) { $temporary = $ids[1]; $ids[1] = $ids[2]; $ids[2] = $temporary }
$reorderRequest = @{ orderedItemIds = $ids; actorId = "demo-parent-mom"; actorName = "林妈妈"; actorRelation = "妈妈" }
$reordered = Invoke-RestMethod -Method Post -Uri "$ApiUrl/plans/$($plan.id)/reorder" -ContentType "application/json; charset=utf-8" -Body (ConvertTo-Utf8JsonBytes $reorderRequest)
if ($reordered.items.Count -ne $ids.Count -or $reordered.items[1].id -ne $ids[1]) { throw "手动调序没有生效" }

Write-Host "9/15 暂时跳过一项困难任务"
$skipTask = $reordered.items | Where-Object status -eq "PENDING" | Select-Object -First 1
$null = Invoke-RestMethod -Method Post -Uri "$ApiUrl/plan-items/$($skipTask.id)/start" -ContentType "application/json; charset=utf-8" -Body (ConvertTo-Utf8JsonBytes $actor)
$skipRequest = $actor + @{ decision = "SKIP"; actualSeconds = 60; extraMinutes = 10 }
$skipped = Invoke-RestMethod -Method Post -Uri "$ApiUrl/plan-items/$($skipTask.id)/overrun-decision" -ContentType "application/json; charset=utf-8" -Body (ConvertTo-Utf8JsonBytes $skipRequest)
if (($skipped.items | Where-Object id -eq $skipTask.id).status -ne "SKIPPED") { throw "暂时跳过没有生效" }

Write-Host "10/15 检查周报趋势并添加鼓励"
$commentRequest = $actor + @{ content = "自动验收：我学会自己调整计划啦！"; weekStart = $null }
$weekly = Invoke-RestMethod -Method Post -Uri "$ApiUrl/children/$($child.id)/weekly-comments" -ContentType "application/json; charset=utf-8" -Body (ConvertTo-Utf8JsonBytes $commentRequest)
if ($weekly.dailyProgress.Count -ne 7 -or -not $weekly.comparison -or -not $weekly.badges -or -not ($weekly.comments | Where-Object content -eq $commentRequest.content)) { throw "周报趋势或鼓励墙写回失败" }

Write-Host "11/15 设置周目标并完成一次性结算"
if ($weekly.goal.status -ne "CLAIMED") {
    $goalRequest = @{ targetTasks = 1; targetFocusMinutes = 1; bonusStars = 2; actorId = "demo-parent-mom"; actorName = "林妈妈"; actorRelation = "妈妈" }
    $goal = Invoke-RestMethod -Method Post -Uri "$ApiUrl/children/$($child.id)/weekly-goal" -ContentType "application/json; charset=utf-8" -Body (ConvertTo-Utf8JsonBytes $goalRequest)
    if ($goal.goal.status -ne "READY") { throw "已达成的周目标没有进入待领取状态" }
    $weekly = Invoke-RestMethod -Method Post -Uri "$ApiUrl/children/$($child.id)/weekly-bonus/claim" -ContentType "application/json; charset=utf-8" -Body (ConvertTo-Utf8JsonBytes $actor)
}
if ($weekly.goal.status -ne "CLAIMED") { throw "周结算奖励没有完成" }

Write-Host "12/15 创建、申请并批准自定义皮肤"
$rewardName = "自动验收皮肤-$([DateTimeOffset]::Now.ToUnixTimeMilliseconds())"
$rewardRequest = @{ name = $rewardName; icon = "🤖"; requiredStars = 1; category = "SKIN"; actorId = "demo-parent-mom"; actorName = "林妈妈"; actorRelation = "妈妈" }
$store = Invoke-RestMethod -Method Post -Uri "$ApiUrl/families/$($context.familyId)/rewards?childId=$($child.id)" -ContentType "application/json; charset=utf-8" -Body (ConvertTo-Utf8JsonBytes $rewardRequest)
$reward = $store.rewards | Where-Object name -eq $rewardName | Select-Object -First 1
$redeemRequest = $actor + @{ childId = $child.id }
$requested = Invoke-RestMethod -Method Post -Uri "$ApiUrl/rewards/$($reward.id)/redeem" -ContentType "application/json; charset=utf-8" -Body (ConvertTo-Utf8JsonBytes $redeemRequest)
$redemption = $requested.rewards | Where-Object id -eq $reward.id
$reviewRequest = @{ approved = $true; actorId = "demo-parent-mom"; actorName = "林妈妈"; actorRelation = "妈妈" }
$reviewed = Invoke-RestMethod -Method Post -Uri "$ApiUrl/reward-redemptions/$($redemption.redemptionId)/review" -ContentType "application/json; charset=utf-8" -Body (ConvertTo-Utf8JsonBytes $reviewRequest)
if (($reviewed.rewards | Where-Object id -eq $reward.id).redemptionStatus -ne "APPROVED") { throw "奖励审批没有完成" }
$equipped = Invoke-RestMethod -Method Post -Uri "$ApiUrl/rewards/$($reward.id)/equip" -ContentType "application/json; charset=utf-8" -Body (ConvertTo-Utf8JsonBytes $redeemRequest)
if ($equipped.equippedSkinRewardId -ne $reward.id) { throw "已解锁皮肤没有成功装备" }

Write-Host "13/15 检查奖励历史与星星账本"
if (-not ($equipped.redemptions | Where-Object id -eq $redemption.redemptionId)) { throw "奖励申请历史缺少刚才的审批" }
if ($equipped.starTransactions.Count -eq 0) { throw "星星账本没有写入" }

Write-Host "14/15 检查家庭协作审计记录"
$activities = Invoke-RestMethod -Uri "$ApiUrl/children/$($child.id)/activities"
foreach ($action in @("TASK_COMPLETED", "PLAN_REORDERED", "PLAN_OVERRUN_DECISION", "REWARD_APPROVED", "DIDI_SKIN_EQUIPPED")) {
    if (-not ($activities | Where-Object actionType -eq $action)) { throw "缺少操作记录：$action" }
}

Write-Host "15/15 阶段 6 闭环通过：OCR 能力 → 识别确认 → 人工修订 → 个性化估时 → 执行与激励。" -ForegroundColor Green
