import { Button, Image, Input, ScrollView, Text, View } from '@tarojs/components';
import Taro, { useDidShow } from '@tarojs/taro';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  gradeLabel, type Activity, type DemoContext, type HomeworkBatch, type RealtimeEvent,
  type RewardStore, type TodayPlan, type WeeklyReport,
} from '@study-time/shared';
import didiMascot from '../../assets/didi-mascot.png';
import './index.css';

const apiBase = process.env.TARO_ENV === 'h5' ? '' : 'http://127.0.0.1:8080';
type Tab = 'homework' | 'plan' | 'growth' | 'rewards';
const parentActor = { actorId: 'demo-parent-mom', actorName: '林妈妈', actorRelation: '妈妈' };

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
  const [tab, setTab] = useState<Tab>('homework');
  const [photoPath, setPhotoPath] = useState('');
  const [startTime, setStartTime] = useState('17:30');
  const [comment, setComment] = useState('这周你越来越会自己安排了，继续加油！');
  const [rewardName, setRewardName] = useState('周末一起去公园');
  const [rewardStars, setRewardStars] = useState('40');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('本地演示身份：林妈妈');

  const child = useMemo(() => context?.children.find((item) => item.id === childId), [childId, context]);

  const loadDashboard = useCallback(async (quiet = false) => {
    try {
      const contextData = context ?? await request<DemoContext>({ url: '/api/v1/demo/context', method: 'GET' });
      setContext(contextData);
      const [planResult, activityResult, weeklyResult, rewardResult] = await Promise.allSettled([
        request<TodayPlan>({ url: `/api/v1/children/${childId}/today-plan`, method: 'GET' }),
        request<Activity[]>({ url: `/api/v1/children/${childId}/activities`, method: 'GET' }),
        request<WeeklyReport>({ url: `/api/v1/children/${childId}/weekly-report`, method: 'GET' }),
        request<RewardStore>({ url: `/api/v1/families/${contextData.familyId}/rewards?childId=${childId}`, method: 'GET' }),
      ]);
      setPlan(planResult.status === 'fulfilled' ? planResult.value : null);
      setActivities(activityResult.status === 'fulfilled' ? activityResult.value : []);
      setWeekly(weeklyResult.status === 'fulfilled' ? weeklyResult.value : null);
      setRewards(rewardResult.status === 'fulfilled' ? rewardResult.value : null);
      if (!quiet) setNotice('孩子端计划、进度与成长数据已同步');
    } catch (error) {
      setNotice(error instanceof Error ? error.message : '暂时无法连接服务');
    }
  }, [childId, context]);

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
      setNotice('照片已选择，可开始模拟识别');
    } catch { setNotice('未选择照片，也可以直接使用预置示例'); }
  };

  const recognize = async () => {
    setBusy(true);
    try {
      let recognized: HomeworkBatch;
      if (photoPath) {
        const response = await Taro.uploadFile({ url: `${apiBase}/api/v1/homework-batches/mock-recognize?childId=${childId}`, filePath: photoPath, name: 'file' });
        if (response.statusCode < 200 || response.statusCode >= 300) throw new Error('照片上传失败');
        recognized = JSON.parse(response.data) as HomeworkBatch;
      } else {
        recognized = await request<HomeworkBatch>({ url: `/api/v1/homework-batches/mock-recognize?childId=${childId}`, method: 'POST' });
      }
      setBatch(recognized);
      setNotice(`嘀嘀识别出 ${recognized.tasks.length} 项作业，请确认`);
    } catch (error) { setNotice(error instanceof Error ? error.message : '识别失败，请重试'); }
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
        data: { ...parentActor, content: comment.trim() },
      });
      setWeekly(updated); setComment(''); setNotice('鼓励已写入家庭鼓励墙');
    } catch (error) { setNotice(error instanceof Error ? error.message : '点评保存失败'); }
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
      <View className="childTabs">{context?.children.map((item) => <Button key={item.id} className={`childTab ${item.id === childId ? 'active' : ''}`} onClick={() => { setChildId(item.id); setBatch(null); }}><Text className="avatar">{item.name.slice(-1)}</Text><View><Text className="childName">{item.name}</Text><Text className="childGrade">{gradeLabel(item.grade)}</Text></View></Button>)}</View>
      <View className="mainTabs">{([['homework','录入作业'],['plan','计划进度'],['growth','成长周报'],['rewards','奖励管理']] as [Tab,string][]).map(([id,label]) => <Button key={id} className={tab === id ? 'active' : ''} onClick={() => setTab(id)}>{label}</Button>)}</View>

      {tab === 'homework' && <>
        <View className="card uploadCard">
          <View className="cardHead"><View><Text className="step">第一步</Text><Text className="cardTitle">录入今天的作业</Text></View><Text className="statusPill">模拟 OCR</Text></View>
          {photoPath ? <Image className="preview" src={photoPath} mode="aspectFill" /> : <View className="photoPlaceholder"><Text className="photoIcon">📷</Text><Text>黑板照片或家长群截图</Text></View>}
          <View className="buttonRow"><Button className="secondaryButton" onClick={() => void choosePhoto()}>选择照片</Button><Button className="primaryButton" loading={busy} disabled={busy} onClick={() => void recognize()}>{photoPath ? '上传并识别' : '使用预置示例'}</Button></View>
        </View>
        {batch && <View className="card">
          <View className="cardHead"><View><Text className="step">第二步</Text><Text className="cardTitle">确认识别结果</Text></View><Text className="pendingPill">待确认</Text></View>
          <View className="recognizedList">{batch.tasks.map((task) => <View className="recognizedItem" key={task.id}><Text className="taskEmoji">{task.icon}</Text><View className="taskText"><Text className="taskSubject">{task.subject} · {task.taskType}</Text><Text className="taskTitle">{task.title}</Text><Text className={`estimateSource ${task.estimateSource === 'HISTORY' ? 'history' : ''}`}>{task.estimateSource === 'HISTORY' ? '按个人历史估算' : '按年级规则估算'}</Text></View><Text className="minutes">{task.estimatedMinutes} 分钟</Text></View>)}</View>
          <View className="startRow"><Text>计划开始时间</Text><Input className="timeInput" value={startTime} onInput={(event) => setStartTime(event.detail.value)} /></View>
          <Button className="confirmButton" loading={busy} disabled={busy} onClick={() => void confirmPlan()}>确认并生成智能计划</Button>
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

      {tab === 'growth' && <View className="card">
        <View className="cardHead"><View><Text className="step">{weekly?.weekStart} 至 {weekly?.weekEnd}</Text><Text className="cardTitle">{child?.name}的成长周报</Text></View><Text className="readyPill">全家可见</Text></View>
        {weekly && <><View className="metricGrid"><View><Text>{weekly.completionRate}%</Text><Text>完成率</Text></View><View><Text>{weekly.focusedMinutes} 分钟</Text><Text>专注时光</Text></View><View><Text>{weekly.streakDays} 天</Text><Text>连续打卡</Text></View></View>
          <Text className="sectionLabel">学科时间曲线</Text><View className="subjectList">{weekly.subjects.map((item) => <View key={item.subject}><Text>{item.subject}</Text><Text>完成 {item.completedTasks} 项 · 预计 {item.averageEstimatedMinutes} / 实际 {item.averageActualMinutes} 分钟</Text></View>)}</View>
          <Text className="sectionLabel">里程碑</Text><View className="badgeRow">{weekly.badges.map((item) => <View className={item.unlocked ? 'unlocked' : ''} key={item.id}><Text>{item.icon}</Text><Text>{item.title}</Text></View>)}</View>
          <Text className="sectionLabel">家庭鼓励墙</Text><View className="activityList">{weekly.comments.map((item) => <View className="activityItem" key={item.id}><Text className="activityActor">{item.actorName} · {item.actorRelation}</Text><Text className="activityText">{item.content}</Text></View>)}</View>
          <View className="inlineForm"><Input value={comment} maxlength={240} onInput={(event) => setComment(event.detail.value)} /><Button disabled={busy} onClick={() => void addComment()}>发送鼓励</Button></View></>}
      </View>}

      {tab === 'rewards' && <View className="card">
        <View className="cardHead"><View><Text className="step">当前 {rewards?.childStars ?? 0} 颗星</Text><Text className="cardTitle">奖励与审批</Text></View><Text className="statusPill">虚拟 + 家庭</Text></View>
        <View className="rewardList">{rewards?.rewards.map((reward) => <View className="rewardItem" key={reward.id}><Text className="rewardIcon">{reward.icon}</Text><View className="taskText"><Text className="taskTitle">{reward.name}</Text><Text className="taskSubject">{reward.requiredStars} 颗星 · {reward.sourceType === 'BUILTIN' ? '内置奖励' : `由${reward.createdByName}设置`}</Text></View>{reward.redemptionStatus === 'REQUESTED' && reward.redemptionId ? <View className="approvalButtons"><Button disabled={busy} onClick={() => void reviewReward(reward.redemptionId!,true)}>批准</Button><Button disabled={busy} onClick={() => void reviewReward(reward.redemptionId!,false)}>暂缓</Button></View> : <Text className="itemStatus">{reward.redemptionStatus === 'APPROVED' ? '已获得' : reward.canRedeem ? '可兑换' : '继续积累'}</Text>}</View>)}</View>
        <Text className="sectionLabel">设置家庭自定义奖励</Text><View className="rewardForm"><Input value={rewardName} placeholder="奖励名称" maxlength={80} onInput={(event) => setRewardName(event.detail.value)} /><Input value={rewardStars} type="number" placeholder="星星" onInput={(event) => setRewardStars(event.detail.value)} /><Button disabled={busy} onClick={() => void createReward()}>添加奖励</Button></View>
      </View>}
    </ScrollView>
  );
}
