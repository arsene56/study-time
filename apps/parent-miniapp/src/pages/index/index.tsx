import { Button, Image, Input, ScrollView, Text, View } from '@tarojs/components';
import Taro, { useDidShow } from '@tarojs/taro';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  gradeLabel, type Activity, type DemoContext, type HomeworkBatch, type RealtimeEvent,
  type PersonalizationProfile, type RecognitionCapability, type RecognizedTask,
  type RewardStore, type TodayPlan, type WeeklyReport,
} from '@study-time/shared';
import didiMascot from '../../assets/didi-mascot.png';
import './index.css';

const apiBase = process.env.TARO_ENV === 'h5' ? '' : 'http://127.0.0.1:8080';
type Tab = 'homework' | 'plan' | 'profile' | 'growth' | 'rewards';
type TaskDraft = Pick<RecognizedTask, 'subject' | 'title' | 'taskType' | 'difficulty' | 'eyeLoad'> & { estimatedMinutes: string };
const parentActor = { actorId: 'demo-parent-mom', actorName: '林妈妈', actorRelation: '妈妈' };

function draftsFromBatch(batch: HomeworkBatch) {
  return Object.fromEntries(batch.tasks.map((task) => [task.id, {
    subject: task.subject, title: task.title, taskType: task.taskType,
    estimatedMinutes: String(task.estimatedMinutes), difficulty: task.difficulty, eyeLoad: task.eyeLoad,
  }])) as Record<string, TaskDraft>;
}

function estimateLabel(source: RecognizedTask['estimateSource']) {
  return {
    GRADE_DEFAULT: '按年级规则估算', PERSONAL_HISTORY: '按同类历史估算',
    SUBJECT_HISTORY: '参考学科历史估算', MANUAL_OVERRIDE: '人工设定',
  }[source];
}

function currentMonday(offsetWeeks: number) {
  const date = new Date();
  const day = date.getDay() || 7;
  date.setHours(12, 0, 0, 0);
  date.setDate(date.getDate() - day + 1 + offsetWeeks * 7);
  return date.toISOString().slice(0, 10);
}

async function request<T>(options: Taro.request.Option): Promise<T> {
  const response = await Taro.request<T>({ ...options, url: `${apiBase}${options.url}` });
  if (response.statusCode < 200 || response.statusCode >= 300) {
    const data = response.data as { message?: string };
    throw new Error(data?.message ?? `请求失败（${response.statusCode}）`);
  }
  return response.data;
}

export default function ParentHome() {
  const [context, setContext] = useState<DemoContext | null>(null);
  const [childId, setChildId] = useState('demo-child-xiaoman');
  const [batch, setBatch] = useState<HomeworkBatch | null>(null);
  const [plan, setPlan] = useState<TodayPlan | null>(null);
  const [activities, setActivities] = useState<Activity[]>([]);
  const [weekly, setWeekly] = useState<WeeklyReport | null>(null);
  const [rewards, setRewards] = useState<RewardStore | null>(null);
  const [profile, setProfile] = useState<PersonalizationProfile | null>(null);
  const [capability, setCapability] = useState<RecognitionCapability | null>(null);
  const [taskDrafts, setTaskDrafts] = useState<Record<string, TaskDraft>>({});
  const [tab, setTab] = useState<Tab>('homework');
  const [photoPath, setPhotoPath] = useState('');
  const [startTime, setStartTime] = useState('17:30');
  const [comment, setComment] = useState('这周你越来越会自己安排了，继续加油！');
  const [rewardName, setRewardName] = useState('周末一起去公园');
  const [rewardStars, setRewardStars] = useState('40');
  const [goalTasks, setGoalTasks] = useState('5');
  const [goalFocus, setGoalFocus] = useState('60');
  const [goalBonus, setGoalBonus] = useState('10');
  const [manualSubject, setManualSubject] = useState('其他');
  const [manualTitle, setManualTitle] = useState('');
  const [manualType, setManualType] = useState('书写');
  const [manualMinutes, setManualMinutes] = useState('15');
  const [weekOffset, setWeekOffset] = useState(0);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('本地演示身份：林妈妈');

  const child = useMemo(() => context?.children.find((item) => item.id === childId), [childId, context]);

  const loadDashboard = useCallback(async (quiet = false) => {
    try {
      const contextData = context ?? await request<DemoContext>({ url: '/api/v1/demo/context', method: 'GET' });
      setContext(contextData);
      const [planResult, activityResult, weeklyResult, rewardResult, profileResult, capabilityResult] = await Promise.allSettled([
        request<TodayPlan>({ url: `/api/v1/children/${childId}/today-plan`, method: 'GET' }),
        request<Activity[]>({ url: `/api/v1/children/${childId}/activities`, method: 'GET' }),
        request<WeeklyReport>({ url: `/api/v1/children/${childId}/weekly-report?weekStart=${currentMonday(weekOffset)}`, method: 'GET' }),
        request<RewardStore>({ url: `/api/v1/families/${contextData.familyId}/rewards?childId=${childId}`, method: 'GET' }),
        request<PersonalizationProfile>({ url: `/api/v1/children/${childId}/personalization-profile`, method: 'GET' }),
        request<RecognitionCapability>({ url: '/api/v1/recognition-capabilities', method: 'GET' }),
      ]);
      setPlan(planResult.status === 'fulfilled' ? planResult.value : null);
      setActivities(activityResult.status === 'fulfilled' ? activityResult.value : []);
      setWeekly(weeklyResult.status === 'fulfilled' ? weeklyResult.value : null);
      if (weekOffset === 0 && weeklyResult.status === 'fulfilled' && weeklyResult.value.goal) {
        setGoalTasks(String(weeklyResult.value.goal.targetTasks));
        setGoalFocus(String(weeklyResult.value.goal.targetFocusMinutes));
        setGoalBonus(String(weeklyResult.value.goal.bonusStars));
      }
      setRewards(rewardResult.status === 'fulfilled' ? rewardResult.value : null);
      setProfile(profileResult.status === 'fulfilled' ? profileResult.value : null);
      setCapability(capabilityResult.status === 'fulfilled' ? capabilityResult.value : null);
      if (!quiet) setNotice('孩子端计划、进度与成长数据已同步');
    } catch (error) {
      setNotice(error instanceof Error ? error.message : '暂时无法连接服务');
    }
  }, [childId, context, weekOffset]);

  useDidShow(() => { void loadDashboard(true); });

  useEffect(() => {
    const initialRefresh = setTimeout(() => void loadDashboard(true), 0);
    const wsBase = apiBase ? apiBase.replace(/^http/, 'ws') : 'ws://127.0.0.1:8080';
    let cancelled = false;
    let socket: Awaited<ReturnType<typeof Taro.connectSocket>> | undefined;
    const connect = async () => {
      socket = await Taro.connectSocket({ url: `${wsBase}/ws/updates?childId=${childId}` });
      if (cancelled) { socket.close({}); return; }
      socket.onMessage((event) => {
        const update = JSON.parse(event.data as string) as RealtimeEvent;
        if (update.childId === childId) void loadDashboard(true);
      });
    };
    void connect();
    return () => { cancelled = true; clearTimeout(initialRefresh); socket?.close({}); };
  }, [childId, loadDashboard]);

  const choosePhoto = async () => {
    try {
      const result = await Taro.chooseMedia({ count: 1, mediaType: ['image'], sourceType: ['album', 'camera'] });
      setPhotoPath(result.tempFiles[0]?.tempFilePath ?? '');
      setNotice('照片已选择，可开始真实识别');
    } catch { setNotice('未选择照片，也可以直接使用预置示例'); }
  };

  const recognize = async () => {
    setBusy(true);
    try {
      let recognized: HomeworkBatch;
      if (photoPath) {
        const response = await Taro.uploadFile({ url: `${apiBase}/api/v1/homework-batches/recognize?childId=${childId}`, filePath: photoPath, name: 'file' });
        if (response.statusCode < 200 || response.statusCode >= 300) throw new Error('照片上传失败');
        recognized = JSON.parse(response.data) as HomeworkBatch;
      } else {
        recognized = await request<HomeworkBatch>({ url: `/api/v1/homework-batches/mock-recognize?childId=${childId}`, method: 'POST' });
      }
      setBatch(recognized); setTaskDrafts(draftsFromBatch(recognized));
      setNotice(recognized.recognitionError
        ? `自动识别需要协助：${recognized.recognitionError}`
        : `嘀嘀识别出 ${recognized.tasks.length} 项作业，请确认`);
    } catch (error) { setNotice(error instanceof Error ? error.message : '识别失败，请重试'); }
    finally { setBusy(false); }
  };

  const updateDraft = <K extends keyof TaskDraft,>(taskId: string, field: K, value: TaskDraft[K]) => {
    setTaskDrafts((current) => ({ ...current, [taskId]: { ...current[taskId], [field]: value } }));
  };

  const saveTask = async (taskId: string) => {
    const draft = taskDrafts[taskId];
    const minutes = Number(draft?.estimatedMinutes);
    if (!draft?.subject.trim() || !draft.title.trim() || !draft.taskType.trim() || !Number.isInteger(minutes) || minutes < 1) {
      setNotice('请完整填写学科、内容、类型和有效分钟数'); return;
    }
    setBusy(true);
    try {
      const updated = await request<HomeworkBatch>({
        url: `/api/v1/homework-tasks/${taskId}`, method: 'PUT', header: { 'Content-Type': 'application/json' },
        data: { ...draft, estimatedMinutes: minutes },
      });
      setBatch(updated); setTaskDrafts(draftsFromBatch(updated)); setNotice('作业项已修订，并标记为人工确认');
    } catch (error) { setNotice(error instanceof Error ? error.message : '保存失败'); }
    finally { setBusy(false); }
  };

  const deleteTask = async (taskId: string) => {
    setBusy(true);
    try {
      const updated = await request<HomeworkBatch>({ url: `/api/v1/homework-tasks/${taskId}`, method: 'DELETE' });
      setBatch(updated); setTaskDrafts(draftsFromBatch(updated)); setNotice('已删除这项识别结果');
    } catch (error) { setNotice(error instanceof Error ? error.message : '删除失败'); }
    finally { setBusy(false); }
  };

  const addManualTask = async () => {
    const minutes = Number(manualMinutes);
    if (!batch || !manualSubject.trim() || !manualTitle.trim() || !manualType.trim() || !Number.isInteger(minutes) || minutes < 1) {
      setNotice('请填写手动作业的学科、内容、类型和分钟数'); return;
    }
    setBusy(true);
    try {
      const updated = await request<HomeworkBatch>({
        url: `/api/v1/homework-batches/${batch.id}/tasks`, method: 'POST', header: { 'Content-Type': 'application/json' },
        data: { subject: manualSubject.trim(), title: manualTitle.trim(), taskType: manualType.trim(), estimatedMinutes: minutes, difficulty: 'MODERATE', eyeLoad: 'HIGH' },
      });
      setBatch(updated); setTaskDrafts(draftsFromBatch(updated)); setManualTitle(''); setNotice('手动作业已加入待确认清单');
    } catch (error) { setNotice(error instanceof Error ? error.message : '添加失败'); }
    finally { setBusy(false); }
  };

  const confirmPlan = async () => {
    if (!batch) return;
    setBusy(true);
    try {
      const created = await request<TodayPlan>({ url: `/api/v1/homework-batches/${batch.id}/confirm-and-plan`, method: 'POST', header: { 'Content-Type': 'application/json' }, data: { startTime } });
      setPlan(created); setBatch(null); setTab('plan');
      setNotice(`计划已生成，预计 ${created.plannedEndTime} 完成`);
      await loadDashboard(true);
    } catch (error) { setNotice(error instanceof Error ? error.message : '生成计划失败'); }
    finally { setBusy(false); }
  };

  const move = async (index: number, direction: -1 | 1) => {
    if (!plan) return;
    const target = index + direction;
    if (target < 0 || target >= plan.items.length) return;
    const ordered = [...plan.items];
    [ordered[index], ordered[target]] = [ordered[target], ordered[index]];
    setBusy(true);
    try {
      const updated = await request<TodayPlan>({
        url: `/api/v1/plans/${plan.id}/reorder`, method: 'POST', header: { 'Content-Type': 'application/json' },
        data: { ...parentActor, orderedItemIds: ordered.map((item) => item.id) },
      });
      setPlan(updated); setNotice('计划顺序已调整，孩子端会实时看到由妈妈调整');
    } catch (error) { setNotice(error instanceof Error ? error.message : '调整失败'); }
    finally { setBusy(false); }
  };

  const addComment = async () => {
    if (!comment.trim()) return;
    setBusy(true);
    try {
      const updated = await request<WeeklyReport>({
        url: `/api/v1/children/${childId}/weekly-comments`, method: 'POST', header: { 'Content-Type': 'application/json' },
        data: { ...parentActor, content: comment.trim(), weekStart: weekly?.weekStart },
      });
      setWeekly(updated); setComment(''); setNotice('鼓励已写入家庭鼓励墙');
    } catch (error) { setNotice(error instanceof Error ? error.message : '点评保存失败'); }
    finally { setBusy(false); }
  };

  const saveWeeklyGoal = async () => {
    const targetTasks = Number(goalTasks);
    const targetFocusMinutes = Number(goalFocus);
    const bonusStars = Number(goalBonus);
    if (![targetTasks, targetFocusMinutes, bonusStars].every((value) => Number.isInteger(value) && value > 0)) {
      setNotice('周目标和奖励星星需要填写正整数'); return;
    }
    setBusy(true);
    try {
      const updated = await request<WeeklyReport>({
        url: `/api/v1/children/${childId}/weekly-goal`, method: 'POST', header: { 'Content-Type': 'application/json' },
        data: { ...parentActor, targetTasks, targetFocusMinutes, bonusStars },
      });
      setWeekly(updated); setNotice('本周成长目标已同步到孩子端');
    } catch (error) { setNotice(error instanceof Error ? error.message : '周目标保存失败'); }
    finally { setBusy(false); }
  };

  const createReward = async () => {
    const stars = Number(rewardStars);
    if (!rewardName.trim() || !Number.isInteger(stars) || stars < 1) { setNotice('请填写奖励名称和有效星星数'); return; }
    if (!context) return;
    setBusy(true);
    try {
      const updated = await request<RewardStore>({
        url: `/api/v1/families/${context.familyId}/rewards?childId=${childId}`, method: 'POST', header: { 'Content-Type': 'application/json' },
        data: { ...parentActor, name: rewardName.trim(), icon: '🎁', requiredStars: stars, category: 'WISH' },
      });
      setRewards(updated); setRewardName(''); setNotice('家庭自定义奖励已添加');
    } catch (error) { setNotice(error instanceof Error ? error.message : '奖励设置失败'); }
    finally { setBusy(false); }
  };

  const reviewReward = async (redemptionId: string, approved: boolean) => {
    setBusy(true);
    try {
      const updated = await request<RewardStore>({
        url: `/api/v1/reward-redemptions/${redemptionId}/review`, method: 'POST', header: { 'Content-Type': 'application/json' }, data: { ...parentActor, approved },
      });
      setRewards(updated); setNotice(approved ? '已批准奖励，星星结算完成' : '已暂缓这次奖励申请');
    } catch (error) { setNotice(error instanceof Error ? error.message : '审批失败'); }
    finally { setBusy(false); }
  };

  return (
    <ScrollView className="page" scrollY>
      <View className="hero"><View><Text className="brand">作业时光 · 家长端</Text><Text className="heroTitle">晚上好，林妈妈</Text><Text className="subtitle">自主规划，快乐成长</Text></View><Image className="didi" src={didiMascot} mode="aspectFit" /></View>
      <View className="notice"><Text>{notice}</Text></View>
      <View className="childTabs">{context?.children.map((item) => <Button key={item.id} className={`childTab ${item.id === childId ? 'active' : ''}`} onClick={() => { setChildId(item.id); setBatch(null); setTaskDrafts({}); }}><Text className="avatar">{item.name.slice(-1)}</Text><View><Text className="childName">{item.name}</Text><Text className="childGrade">{gradeLabel(item.grade)}</Text></View></Button>)}</View>
      <View className="mainTabs">{([['homework','录入'],['plan','计划'],['profile','个性化'],['growth','周报'],['rewards','奖励']] as [Tab,string][]).map(([id,label]) => <Button key={id} className={tab === id ? 'active' : ''} onClick={() => setTab(id)}>{label}</Button>)}</View>

      {tab === 'homework' && <>
        <View className="card uploadCard">
          <View className="cardHead"><View><Text className="step">第一步</Text><Text className="cardTitle">录入今天的作业</Text></View><Text className={`statusPill ${capability?.realOcrAvailable ? 'ocrReady' : ''}`}>{capability?.realOcrAvailable ? '真实 OCR' : '演示 / 手动'}</Text></View>
          {capability && <View className="capability"><Text>{capability.realOcrAvailable ? '● 已就绪' : '○ 待配置'} · {capability.providerLabel}</Text><Text>{capability.message}</Text></View>}
          {photoPath ? <Image className="preview" src={photoPath} mode="aspectFill" /> : <View className="photoPlaceholder"><Text className="photoIcon">📷</Text><Text>黑板照片或家长群截图</Text></View>}
          <View className="buttonRow"><Button className="secondaryButton" onClick={() => void choosePhoto()}>选择照片</Button><Button className="primaryButton" loading={busy} disabled={busy} onClick={() => void recognize()}>{photoPath ? '上传并识别' : '使用预置示例'}</Button></View>
        </View>
        {batch && <View className="card">
          <View className="cardHead"><View><Text className="step">第二步</Text><Text className="cardTitle">确认并修订识别结果</Text></View><Text className="pendingPill">{batch.status === 'NEEDS_MANUAL_ENTRY' ? '需手动录入' : '待确认'}</Text></View>
          <View className="ocrMeta"><Text>来源：{batch.ocrProvider ?? '未知'}{batch.ocrAverageConfidence != null ? ` · 平均置信度 ${Math.round(batch.ocrAverageConfidence)}%` : ''}</Text>{batch.recognitionError && <Text className="ocrError">⚠ {batch.recognitionError}</Text>}{batch.ocrRawText && <Text className="ocrText">识别原文：{batch.ocrRawText}</Text>}</View>
          <View className="recognizedList">{batch.tasks.map((task) => <View className="recognizedEditor" key={task.id}>
            <View className="editorHead"><Text className="taskEmoji">{task.icon}</Text><View><Text className="estimateTitle">{estimateLabel(task.estimateSource)} · 基准 {task.baseEstimatedMinutes} → 当前 {task.estimatedMinutes} 分钟</Text><Text className="estimateReason">{task.estimateReason}{task.estimateSampleSize ? ` · ${task.estimateSampleSize} 个样本` : ''}</Text></View>{task.ocrConfidence != null && <Text className={`confidence confidence-${task.confidence.toLowerCase()}`}>{Math.round(task.ocrConfidence)}%</Text>}</View>
            <View className="editGrid"><Input value={taskDrafts[task.id]?.subject ?? task.subject} placeholder="学科" onInput={(event) => updateDraft(task.id,'subject',event.detail.value)} /><Input value={taskDrafts[task.id]?.taskType ?? task.taskType} placeholder="任务类型" onInput={(event) => updateDraft(task.id,'taskType',event.detail.value)} /><Input className="wideInput" value={taskDrafts[task.id]?.title ?? task.title} placeholder="作业内容" onInput={(event) => updateDraft(task.id,'title',event.detail.value)} /><Input value={taskDrafts[task.id]?.estimatedMinutes ?? String(task.estimatedMinutes)} type="number" placeholder="分钟" onInput={(event) => updateDraft(task.id,'estimatedMinutes',event.detail.value)} /></View>
            <View className="choiceRow"><Text>难度</Text>{(['EASY','MODERATE','CHALLENGE'] as const).map((value) => <Button key={value} className={(taskDrafts[task.id]?.difficulty ?? task.difficulty) === value ? 'selected' : ''} onClick={() => updateDraft(task.id,'difficulty',value)}>{value === 'EASY' ? '轻松' : value === 'MODERATE' ? '适中' : '挑战'}</Button>)}<Button className={(taskDrafts[task.id]?.eyeLoad ?? task.eyeLoad) === 'LOW' ? 'selected' : ''} onClick={() => updateDraft(task.id,'eyeLoad',(taskDrafts[task.id]?.eyeLoad ?? task.eyeLoad) === 'LOW' ? 'HIGH' : 'LOW')}>{(taskDrafts[task.id]?.eyeLoad ?? task.eyeLoad) === 'LOW' ? '低用眼' : '高用眼'}</Button></View>
            <View className="editorActions"><Button disabled={busy} onClick={() => void saveTask(task.id)}>保存修订</Button><Button disabled={busy} onClick={() => void deleteTask(task.id)}>删除</Button></View>
          </View>)}</View>
          <Text className="sectionLabel">识别不清？手动补一项</Text><View className="manualTaskForm"><Input value={manualSubject} placeholder="学科" onInput={(event) => setManualSubject(event.detail.value)} /><Input value={manualType} placeholder="类型" onInput={(event) => setManualType(event.detail.value)} /><Input className="wideInput" value={manualTitle} placeholder="输入作业内容" onInput={(event) => setManualTitle(event.detail.value)} /><Input value={manualMinutes} type="number" placeholder="分钟" onInput={(event) => setManualMinutes(event.detail.value)} /><Button disabled={busy} onClick={() => void addManualTask()}>＋ 添加作业</Button></View>
          <View className="startRow"><Text>计划开始时间</Text><Input className="timeInput" value={startTime} onInput={(event) => setStartTime(event.detail.value)} /></View>
          <Button className="confirmButton" loading={busy} disabled={busy || batch.tasks.length === 0} onClick={() => void confirmPlan()}>确认 {batch.tasks.length} 项并生成智能计划</Button>
        </View>}
      </>}

      {tab === 'plan' && <>
        {!plan ? <View className="card emptyCard"><Text>今天还没有计划，请先录入作业。</Text></View> : <View className="card planCard">
          <View className="cardHead"><View><Text className="step">今日计划 · 版本 {plan.version}</Text><Text className="cardTitle">{child?.name}预计 {plan.plannedEndTime} 完成</Text></View><Text className="readyPill">实时同步</Text></View>
          {plan.warningMessage && <View className="warning"><Text>⏰ {plan.warningMessage}</Text></View>}
          <Text className="buffer">已预留睡前活动 {plan.bedtimeBufferMinutes} 分钟 · 上下按钮可调整顺序</Text>
          {plan.items.map((item,index) => <View className={`planItem ${item.status === 'DONE' || item.status === 'SKIPPED' ? 'done' : ''}`} key={item.id}><Text className="planTime">{item.plannedStart}</Text><Text className="taskEmoji">{item.icon}</Text><View className="taskText"><Text className="taskTitle">{item.title}</Text><Text className="taskSubject">{item.subject} · {item.plannedStart}–{item.plannedEnd}</Text></View><View className="planControls"><Text className={`itemStatus status-${item.status.toLowerCase()}`}>{item.status === 'DONE' ? '已完成' : item.status === 'SKIPPED' ? '已跳过' : item.status === 'ACTIVE' ? '进行中' : '待完成'}</Text><View className="orderRow"><Button disabled={busy || index === 0} onClick={() => void move(index,-1)}>↑</Button><Button disabled={busy || index === plan.items.length-1} onClick={() => void move(index,1)}>↓</Button></View></View></View>)}
        </View>}
        <View className="card"><View className="cardHead"><View><Text className="step">家庭协作记录</Text><Text className="cardTitle">谁调整了什么</Text></View></View><View className="activityList">{activities.slice(0,12).map((item) => <View className="activityItem" key={item.id}><Text className="activityActor">{item.actorName} · {item.actorRelation}</Text><Text className="activityText">{item.description}</Text><Text className="activityTime">{item.createdAt}</Text></View>)}</View></View>
      </>}

      {tab === 'profile' && <View className="card profileCard">
        <View className="cardHead"><View><Text className="step">最近 60 天 · 滚动学习</Text><Text className="cardTitle">{child?.name}的时间曲线</Text></View><Text className={`profileLevel level-${profile?.level.toLowerCase()}`}>{profile?.level === 'STABLE' ? '稳定画像' : profile?.level === 'LEARNING' ? '学习中' : '刚开始'}</Text></View>
        {profile && <><View className="profileHero"><View><Text>{profile.totalSamples}</Text><Text>有效完成样本</Text></View><View><Text>{profile.overallPacePercent}%</Text><Text>相对年级基准</Text></View><View><Text>{profile.confidence === 'HIGH' ? '高' : profile.confidence === 'MEDIUM' ? '中' : '低'}</Text><Text>估时可信度</Text></View></View>
          <View className="growthMessage"><Text>🤖</Text><View><Text>{profile.summary}</Text><Text>新近记录权重更高，单次异常用时不会直接改变后续计划。</Text></View></View>
          <Text className="sectionLabel">分学科节奏</Text>
          {profile.subjects.length === 0 ? <View className="emptyProfile"><Text>孩子完成作业并记录实际用时后，这里会逐步形成专属曲线。</Text></View> : <View className="profileSubjects">{profile.subjects.map((item) => <View key={item.subject}><View className="profileSubjectHead"><Text>{item.subject}</Text><Text>{item.sampleSize} 个样本</Text></View><View className="paceTrack"><View style={{ width: `${Math.min(100,Math.max(8,item.pacePercent / 1.5))}%` }} /></View><Text className="profileTrend">{item.trend} · 预计均值 {item.averageEstimatedMinutes} 分钟 / 实际 {item.averageActualMinutes} 分钟</Text><Text className="profileSuggestion">{item.suggestion}</Text></View>)}</View>}
          <View className="privacyNote"><Text>只使用当前孩子自己的完成记录，多孩数据互不参与估算。</Text></View></>}
      </View>}

      {tab === 'growth' && <View className="card">
        <View className="cardHead"><View><Text className="step">{weekly?.weekStart} 至 {weekly?.weekEnd}</Text><Text className="cardTitle">{child?.name}的成长周报</Text></View><View className="weekControls"><Button onClick={() => setWeekOffset((value) => value - 1)}>‹</Button><Button disabled={weekOffset === 0} onClick={() => setWeekOffset((value) => Math.min(0,value + 1))}>›</Button></View></View>
        {weekly && <><View className="metricGrid"><View><Text>{weekly.completionRate}%</Text><Text>完成率</Text></View><View><Text>{weekly.focusedMinutes} 分钟</Text><Text>专注时光</Text></View><View><Text>{weekly.streakDays} 天</Text><Text>连续打卡</Text></View></View>
          <View className="growthMessage"><Text>🤖</Text><View><Text>{weekly.growthMessage}</Text><Text>{weekly.comparison.trendText}</Text></View></View>
          <Text className="sectionLabel">一周趋势</Text><View className="dailyBars">{weekly.dailyProgress.map((day) => <View key={day.date}><View className="bar"><View style={{ height: `${Math.max(day.completionRate ? 10 : 0,day.completionRate)}%` }} /></View><Text>{day.dayLabel.slice(1)}</Text><Text>{day.completedTasks}/{day.totalTasks}</Text></View>)}</View>
          {weekly.goal && <View className={`goalCard status-${weekly.goal.status.toLowerCase()}`}><View className="goalTitle"><View><Text>本周成长目标</Text><Text>{weekly.goal.status === 'CLAIMED' ? '奖励已领取' : weekly.goal.achieved ? '已达成，等待孩子领取' : `进度 ${weekly.goal.overallProgress}%`}</Text></View><Text>+{weekly.goal.bonusStars} ⭐</Text></View><View className="goalLine"><Text>完成 {weekly.completedTasks}/{weekly.goal.targetTasks} 项</Text><Text>专注 {weekly.focusedMinutes}/{weekly.goal.targetFocusMinutes} 分钟</Text></View></View>}
          {weekOffset === 0 && weekly.goal?.status !== 'CLAIMED' && <><Text className="sectionLabel">设置本周目标</Text><View className="goalForm"><View><Text>作业项数</Text><Input value={goalTasks} type="number" onInput={(event) => setGoalTasks(event.detail.value)} /></View><View><Text>专注分钟</Text><Input value={goalFocus} type="number" onInput={(event) => setGoalFocus(event.detail.value)} /></View><View><Text>奖励星星</Text><Input value={goalBonus} type="number" onInput={(event) => setGoalBonus(event.detail.value)} /></View><Button disabled={busy} onClick={() => void saveWeeklyGoal()}>保存并同步</Button></View></>}
          <Text className="sectionLabel">学科时间曲线</Text><View className="subjectList">{weekly.subjects.map((item) => <View key={item.subject}><Text>{item.subject}</Text><Text>完成 {item.completedTasks} 项 · 预计 {item.averageEstimatedMinutes} / 实际 {item.averageActualMinutes} 分钟</Text></View>)}</View>
          <Text className="sectionLabel">里程碑</Text><View className="badgeRow">{weekly.badges.map((item) => <View className={item.unlocked ? 'unlocked' : ''} key={item.id}><Text>{item.icon}</Text><Text>{item.title}</Text><Text>{item.unlocked ? '已解锁' : `${item.progress}/${item.target}`}</Text></View>)}</View>
          <Text className="sectionLabel">家庭鼓励墙</Text><View className="activityList">{weekly.comments.map((item) => <View className="activityItem" key={item.id}><Text className="activityActor">{item.actorName} · {item.actorRelation}</Text><Text className="activityText">{item.content}</Text></View>)}</View>
          <View className="inlineForm"><Input value={comment} maxlength={240} onInput={(event) => setComment(event.detail.value)} /><Button disabled={busy} onClick={() => void addComment()}>发送鼓励</Button></View></>}
      </View>}

      {tab === 'rewards' && <View className="card">
        <View className="cardHead"><View><Text className="step">当前 {rewards?.childStars ?? 0} 颗星</Text><Text className="cardTitle">奖励与审批</Text></View><Text className="statusPill">虚拟 + 家庭</Text></View>
        <View className="rewardList">{rewards?.rewards.map((reward) => <View className={`rewardItem ${reward.equipped ? 'equipped' : ''}`} key={reward.id}>
          <Text className="rewardIcon">{reward.icon}</Text>
          <View className="taskText"><Text className="taskTitle">{reward.name}</Text><Text className="taskSubject">{reward.requiredStars} 颗星 · {reward.sourceType === 'BUILTIN' ? '内置奖励' : `由${reward.createdByName}设置`}</Text></View>
          {reward.redemptionStatus === 'REQUESTED' && reward.redemptionId
            ? <View className="approvalButtons"><Button disabled={busy} onClick={() => void reviewReward(reward.redemptionId!,true)}>批准</Button><Button disabled={busy} onClick={() => void reviewReward(reward.redemptionId!,false)}>暂缓</Button></View>
            : <Text className="itemStatus">{reward.equipped ? '正在使用' : reward.category === 'SKIN' && reward.owned ? '已拥有' : reward.canRedeem ? '可兑换' : '继续积累'}</Text>}
        </View>)}</View>
        <Text className="sectionLabel">设置家庭自定义奖励</Text><View className="rewardForm"><Input value={rewardName} placeholder="奖励名称" maxlength={80} onInput={(event) => setRewardName(event.detail.value)} /><Input value={rewardStars} type="number" placeholder="星星" onInput={(event) => setRewardStars(event.detail.value)} /><Button disabled={busy} onClick={() => void createReward()}>添加奖励</Button></View>
        <Text className="sectionLabel">奖励申请记录</Text><View className="historyList">{rewards?.redemptions.map((item) => <View key={item.id}><Text className="historyIcon">{item.rewardIcon}</Text><View><Text className="historyTitle">{item.rewardName}</Text><Text className="historyMeta">{item.requestedByName}申请 · {item.requestedAt}</Text></View><Text className={`historyStatus status-${item.status.toLowerCase()}`}>{item.status === 'REQUESTED' ? '待审批' : item.status === 'APPROVED' ? '已批准' : '已暂缓'}</Text></View>)}</View>
        <Text className="sectionLabel">星星账本</Text><View className="historyList">{rewards?.starTransactions.map((item) => <View key={item.id}><Text className="historyIcon">{item.amount > 0 ? '⭐' : '🎁'}</Text><View><Text className="historyTitle">{item.reason}</Text><Text className="historyMeta">{item.createdAt}</Text></View><Text className={item.amount > 0 ? 'starPlus' : 'starMinus'}>{item.amount > 0 ? '+' : ''}{item.amount}</Text></View>)}</View>
      </View>}
    </ScrollView>
  );
}
