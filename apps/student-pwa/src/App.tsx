import {
  ArrowDown, ArrowUp, Check, ChevronLeft, ChevronRight, CircleAlert, Clock3, Flame, Gift,
  History, Pause, Play, RefreshCw, Sparkles, Star, Trophy, WalletCards,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type CSSProperties } from 'react';
import {
  demoStudentId, gradeLabel, type DemoContext, type PlanItem, type RealtimeEvent,
  type RewardStore, type TodayPlan, type WeeklyReport,
} from '@study-time/shared';

const apiBase = import.meta.env.VITE_API_BASE ?? '';
const pageParams = new URLSearchParams(window.location.search);
const hasUnsupportedQuery = Array.from(pageParams.keys()).some((key) => key !== 'studentId');
const selectedStudentId = pageParams.get('studentId') ?? demoStudentId;
type Tab = 'plan' | 'growth' | 'rewards';
type RewardFilter = 'ALL' | 'SKIN' | 'WISH';
type RewardPanel = 'STORE' | 'HISTORY' | 'LEDGER';

function currentMonday(offsetWeeks: number) {
  const date = new Date();
  const day = date.getDay() || 7;
  date.setHours(12, 0, 0, 0);
  date.setDate(date.getDate() - day + 1 + offsetWeeks * 7);
  return date.toISOString().slice(0, 10);
}

async function readJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBase}${path}`, init);
  if (!response.ok) {
    const error = (await response.json().catch(() => null)) as { message?: string } | null;
    throw new Error(error?.message ?? `请求失败（${response.status}）`);
  }
  return response.json() as Promise<T>;
}

function taskTone(item: PlanItem) {
  if (item.kind === 'BREAK') return 'break';
  if (item.kind === 'ROUTINE') return 'routine';
  return 'homework';
}

function actor(studentName?: string) {
  return {
    actorId: selectedStudentId.replace('demo-student-', 'demo-student-member-'),
    actorName: studentName ?? '小满',
    actorRelation: '学生',
  };
}

function elapsedSeconds(item: PlanItem, now: number) {
  if (!item.startedAt) return Math.max(0, item.actualSeconds);
  const started = new Date(item.startedAt.replace(' ', 'T')).getTime();
  return Number.isNaN(started) ? item.actualSeconds : Math.max(1, Math.floor((now - started) / 1000));
}

function formatElapsed(seconds: number) {
  const minutes = Math.floor(seconds / 60).toString().padStart(2, '0');
  const rest = Math.floor(seconds % 60).toString().padStart(2, '0');
  return `${minutes}:${rest}`;
}

export default function App() {
  if (hasUnsupportedQuery) {
    return (
      <main className="invalid-link-shell">
        <section className="invalid-link-card" role="alert">
          <CircleAlert aria-hidden="true" />
          <p>这个设备链接已经失效</p>
          <h1>请使用新的学生端链接</h1>
          <span>请从家长端重新扫码绑定，或使用带有 studentId 的学生链接。</span>
          <a href="/">返回学生端首页</a>
        </section>
      </main>
    );
  }

  return <StudentApp />;
}

function StudentApp() {
  const [context, setContext] = useState<DemoContext | null>(null);
  const [plan, setPlan] = useState<TodayPlan | null>(null);
  const [weekly, setWeekly] = useState<WeeklyReport | null>(null);
  const [rewards, setRewards] = useState<RewardStore | null>(null);
  const [tab, setTab] = useState<Tab>('plan');
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [message, setMessage] = useState('正在和嘀嘀连接…');
  const [comment, setComment] = useState('这周我更会自己安排时间了！');
  const [now, setNow] = useState(0);
  const [weekOffset, setWeekOffset] = useState(0);
  const [rewardFilter, setRewardFilter] = useState<RewardFilter>('ALL');
  const [rewardPanel, setRewardPanel] = useState<RewardPanel>('STORE');
  const [celebration, setCelebration] = useState<{ title: string; detail: string } | null>(null);

  const loadDashboard = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const contextData = await readJson<DemoContext>('/api/v1/demo/context');
      setContext(contextData);
      const [planResult, weeklyResult, rewardResult] = await Promise.allSettled([
        readJson<TodayPlan>(`/api/v1/students/${selectedStudentId}/today-plan`),
        readJson<WeeklyReport>(`/api/v1/students/${selectedStudentId}/weekly-report?weekStart=${currentMonday(weekOffset)}`),
        readJson<RewardStore>(`/api/v1/families/${contextData.familyId}/rewards?studentId=${selectedStudentId}`),
      ]);
      setPlan(planResult.status === 'fulfilled' ? planResult.value : null);
      setWeekly(weeklyResult.status === 'fulfilled' ? weeklyResult.value : null);
      setRewards(rewardResult.status === 'fulfilled' ? rewardResult.value : null);
      setMessage('计划已和家长端实时同步');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '暂时无法连接嘀嘀');
    } finally {
      setLoading(false);
    }
  }, [weekOffset]);

  useEffect(() => {
    const initialRefresh = window.setTimeout(() => void loadDashboard(), 0);
    return () => window.clearTimeout(initialRefresh);
  }, [loadDashboard]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = apiBase ? new URL(apiBase).host : window.location.host;
    const socket = new WebSocket(`${protocol}//${host}/ws/updates?studentId=${selectedStudentId}`);
    socket.onmessage = (event) => {
      const update = JSON.parse(event.data) as RealtimeEvent;
      if (update.studentId === selectedStudentId) void loadDashboard(true);
    };
    return () => socket.close();
  }, [loadDashboard]);

  const student = context?.students.find((item) => item.id === selectedStudentId);
  const finished = plan?.items.filter((item) => item.status === 'DONE' || item.status === 'SKIPPED').length ?? 0;
  const total = plan?.items.length ?? 0;
  const progress = total === 0 ? 0 : Math.round((finished / total) * 100);
  const nextItem = useMemo(
    () => plan?.items.find((item) => item.status === 'ACTIVE')
      ?? plan?.items.find((item) => item.status === 'PENDING') ?? null,
    [plan],
  );

  const mutatePlan = async (path: string, body: object, success: string) => {
    setBusyId(path);
    try {
      const updated = await readJson<TodayPlan>(path, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
      });
      setPlan(updated);
      setMessage(success);
      return updated;
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '操作失败，请再试一次');
      return null;
    } finally {
      setBusyId(null);
    }
  };

  const start = (item: PlanItem) => mutatePlan(
    `/api/v1/plan-items/${item.id}/start`, actor(student?.name), `“${item.title}”开始计时，加油！`,
  );

  const complete = async (item: PlanItem) => {
    const updated = await mutatePlan(
      `/api/v1/plan-items/${item.id}/complete`,
      { ...actor(student?.name), actualSeconds: elapsedSeconds(item, now) },
      item.kind === 'BREAK' ? '休息完成，眼睛也充好电啦' : '完成一项！嘀嘀为你点亮了星星',
    );
    if (updated?.items.every((task) => task.status === 'DONE' || task.status === 'SKIPPED')) {
      setCelebration({ title: '今日任务全部完成！', detail: '嘀嘀的专属闪光已点亮，接下来是快乐晚间时光' });
      setMessage('今天的任务全部完成，嘀嘀专属闪光已解锁！');
      window.setTimeout(() => setCelebration(null), 3600);
    }
  };

  const decide = (item: PlanItem, decision: 'SKIP' | 'CONTINUE') => mutatePlan(
    `/api/v1/plan-items/${item.id}/overrun-decision`,
    { ...actor(student?.name), decision, actualSeconds: elapsedSeconds(item, now), extraMinutes: 10 },
    decision === 'SKIP' ? '已暂时跳过，嘀嘀重新安排了后面的任务' : '继续挑战 10 分钟，后续时间已自动更新',
  );

  const move = async (index: number, direction: -1 | 1) => {
    if (!plan) return;
    const target = index + direction;
    if (target < 0 || target >= plan.items.length) return;
    const ordered = [...plan.items];
    [ordered[index], ordered[target]] = [ordered[target], ordered[index]];
    await mutatePlan(
      `/api/v1/plans/${plan.id}/reorder`,
      { ...actor(student?.name), orderedItemIds: ordered.map((item) => item.id) },
      '顺序已调整，家长端也能看到这次变化',
    );
  };

  const addComment = async () => {
    if (!comment.trim()) return;
    setBusyId('comment');
    try {
      const updated = await readJson<WeeklyReport>(`/api/v1/students/${selectedStudentId}/weekly-comments`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...actor(student?.name), content: comment.trim(), weekStart: weekly?.weekStart }),
      });
      setWeekly(updated);
      setComment('');
      setMessage('周总结已保存，爸爸妈妈也能看到');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '暂时无法保存周总结');
    } finally {
      setBusyId(null);
    }
  };

  const redeem = async (rewardId: string, rewardName: string) => {
    setBusyId(rewardId);
    try {
      const updated = await readJson<RewardStore>(`/api/v1/rewards/${rewardId}/redeem`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ studentId: selectedStudentId, ...actor(student?.name) }),
      });
      setRewards(updated);
      setMessage(`“${rewardName}”已发给家长审批`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '兑换申请没有发出去');
    } finally {
      setBusyId(null);
    }
  };

  const claimWeeklyBonus = async () => {
    setBusyId('weekly-bonus');
    try {
      const updated = await readJson<WeeklyReport>(`/api/v1/students/${selectedStudentId}/weekly-bonus/claim`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(actor(student?.name)),
      });
      setWeekly(updated);
      setCelebration({ title: '成长目标达成！', detail: '嘀嘀的专属闪光已点亮，奖励星星也到账啦' });
      setMessage(`本周目标达成！${updated.goal?.bonusStars ?? 0} 颗奖励星已到账`);
      window.setTimeout(() => setCelebration(null), 3600);
      if (context) setRewards(await readJson<RewardStore>(`/api/v1/families/${context.familyId}/rewards?studentId=${selectedStudentId}`));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '周奖励暂时无法领取');
    } finally {
      setBusyId(null);
    }
  };

  const equipSkin = async (rewardId: string, rewardName: string) => {
    setBusyId(rewardId);
    try {
      const updated = await readJson<RewardStore>(`/api/v1/rewards/${rewardId}/equip`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ studentId: selectedStudentId, ...actor(student?.name) }),
      });
      setRewards(updated);
      setMessage(`嘀嘀已经换上“${rewardName}”`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '皮肤暂时无法使用');
    } finally {
      setBusyId(null);
    }
  };

  const visibleRewards = rewards?.rewards.filter(
    (reward) => rewardFilter === 'ALL' || reward.category === rewardFilter,
  ) ?? [];
  const skinClass = rewards?.equippedSkinRewardId?.includes('sunset')
    ? 'skin-sunset'
    : rewards?.equippedSkinRewardId?.includes('starlight') ? 'skin-starlight' : 'skin-mint';

  return (
    <main className="app-shell">
      <header className="hero-card">
        <div className="hero-copy">
          <p className="eyebrow">作业时光 · 学生端</p>
          <h1>{student ? `${student.name}，今天也一起加油` : '嘀嘀正在准备今天的计划'}</h1>
          <p className="sync-line"><span className="live-dot" />{message}</p>
        </div>
        <div className={`didi-wrap ${skinClass}`} aria-label="小时光机器人嘀嘀">
          <img src="/didi-mascot.png" alt="小时光机器人嘀嘀" /><span>嘀嘀</span>
        </div>
      </header>

      {celebration && <output className="celebration"><div><span>✨</span><strong>{celebration.title}</strong><p>{celebration.detail}</p></div></output>}

      <section className="summary-grid" aria-label="今日计划概览">
        <article><Clock3 /><span>预计完成</span><strong>{plan?.plannedEndTime ?? '--:--'}</strong></article>
        <article><Star /><span>我的星星</span><strong>{rewards?.studentStars ?? student?.stars ?? '--'}</strong></article>
        <article><Sparkles /><span>当前进度</span><strong>{progress}%</strong></article>
      </section>

      <nav className="main-tabs" aria-label="主要功能">
        <button className={tab === 'plan' ? 'active' : ''} onClick={() => setTab('plan')}><Clock3 />今日计划</button>
        <button className={tab === 'growth' ? 'active' : ''} onClick={() => setTab('growth')}><Trophy />成长周报</button>
        <button className={tab === 'rewards' ? 'active' : ''} onClick={() => setTab('rewards')}><Gift />星星奖励</button>
      </nav>

      {tab === 'plan' && (
        <section className="plan-section">
          <div className="section-heading">
            <div><p>{student ? gradeLabel(student.grade) : '今日任务'}</p><h2>我的放学后计划</h2></div>
            <button className="icon-button" type="button" onClick={() => void loadDashboard()} aria-label="刷新计划"><RefreshCw className={loading ? 'spin' : ''} /></button>
          </div>
          {plan?.warningMessage && <div className="warning-card">⏰ {plan.warningMessage}</div>}
          {plan && <div className="buffer-note">嘀嘀已为睡前活动预留 {plan.bedtimeBufferMinutes} 分钟</div>}
          {!loading && !plan && <div className="empty-card"><div className="empty-icon">📷</div><h3>计划还在路上</h3><p>请让家长识别作业并确认计划，生成后这里会自动出现。</p></div>}
          {loading && <div className="loading-card">嘀嘀正在整理任务顺序…</div>}
          <div className="task-list">
            {plan?.items.map((item, index) => {
              const isNext = nextItem?.id === item.id;
              const terminal = item.status === 'DONE' || item.status === 'SKIPPED';
              const active = item.status === 'ACTIVE';
              return (
                <article className={`task-card ${taskTone(item)} ${terminal ? 'done' : ''} ${active ? 'active-task' : ''}`} key={item.id}>
                  <div className="task-time"><strong>{item.plannedStart}</strong><span>{item.estimatedMinutes} 分钟</span></div>
                  <div className="task-icon" aria-hidden="true">{item.icon}</div>
                  <div className="task-copy">
                    <div className="task-labels"><span>{item.subject}</span>{isNext && <em>{active ? '进行中' : '接下来'}</em>}</div>
                    <h3>{item.title}</h3><p>{item.taskType} · {item.plannedStart}–{item.plannedEnd}</p>
                    {active && <strong className="elapsed">已专注 {formatElapsed(elapsedSeconds(item, now))}</strong>}
                    {item.status === 'SKIPPED' && <strong className="skipped-label">已暂时跳过</strong>}
                  </div>
                  <div className="task-actions">
                    {!terminal && <div className="order-buttons">
                      <button disabled={index === 0 || busyId !== null} onClick={() => void move(index, -1)} aria-label="上移"><ArrowUp /></button>
                      <button disabled={index === (plan?.items.length ?? 0) - 1 || busyId !== null} onClick={() => void move(index, 1)} aria-label="下移"><ArrowDown /></button>
                    </div>}
                    {item.status === 'PENDING' && <button type="button" className="start-button" disabled={busyId !== null || !isNext} onClick={() => void start(item)}><Play />{isNext ? '开始' : '等待'}</button>}
                    {active && <button type="button" className="complete-button" disabled={busyId !== null} onClick={() => void complete(item)}><Check />完成</button>}
                    {terminal && <span className="terminal-status"><Check />{item.status === 'DONE' ? '完成' : '跳过'}</span>}
                  </div>
                  {active && item.kind === 'HOMEWORK' && <div className="challenge-panel">
                    <span>遇到困难或已经超时？你来决定：</span>
                    <button disabled={busyId !== null} onClick={() => void decide(item, 'SKIP')}><Pause />暂时跳过</button>
                    <button disabled={busyId !== null} onClick={() => void decide(item, 'CONTINUE')}><Flame />继续挑战 10 分钟</button>
                  </div>}
                </article>
              );
            })}
          </div>
        </section>
      )}

      {tab === 'growth' && (
        <section className="content-section">
          <div className="section-heading"><div><p>每周一起回顾</p><h2>我的成长周报</h2></div><div className="week-switch"><button onClick={() => setWeekOffset((value) => value - 1)} aria-label="上一周"><ChevronLeft /></button><button disabled={weekOffset === 0} onClick={() => setWeekOffset((value) => Math.min(0, value + 1))} aria-label="下一周"><ChevronRight /></button></div></div>
          {!weekly ? <div className="loading-card">正在准备本周成长记录…</div> : <>
            <div className="weekly-hero">
              <div className="ring" style={{ '--progress': `${weekly.completionRate * 3.6}deg` } as CSSProperties}><strong>{weekly.completionRate}%</strong><span>完成率</span></div>
              <div><p>{weekly.weekStart} 至 {weekly.weekEnd}</p><h3>已完成 {weekly.completedTasks}/{weekly.totalTasks} 项作业</h3><span>专注 {weekly.focusedMinutes} 分钟 · 收获 {weekly.starsEarned} 颗星</span><em>{weekly.comparison.trendText}</em></div>
            </div>
            <div className="didi-message"><Sparkles /><p>{weekly.growthMessage}</p></div>
            <div className="growth-stats"><article><Flame /><strong>{weekly.streakDays} 天</strong><span>连续打卡</span></article><article><Clock3 /><strong>{weekly.focusedMinutes} 分钟</strong><span>专注时光</span></article><article><Star /><strong>{weekly.starsEarned}</strong><span>本周星星</span></article></div>
            <h3 className="subheading">一周成长轨迹</h3>
            <div className="daily-chart">{weekly.dailyProgress.map((day) => <article key={day.date}><span className="bar-track"><i style={{ height: `${Math.max(day.completionRate ? 12 : 0, day.completionRate)}%` }} /></span><strong>{day.dayLabel}</strong><small>{day.completedTasks}/{day.totalTasks}</small></article>)}</div>
            {weekly.goal && <div className={`weekly-goal ${weekly.goal.status.toLowerCase()}`}>
              <div className="goal-head"><div><small>本周成长目标</small><h3>{weekly.goal.status === 'CLAIMED' ? '奖励已经收入星星袋' : weekly.goal.achieved ? '目标达成，可以领取啦！' : '稳稳向目标前进'}</h3></div><strong>+{weekly.goal.bonusStars} ⭐</strong></div>
              <div className="goal-progress"><div><span>完成 {weekly.completedTasks}/{weekly.goal.targetTasks} 项作业</span><b><i style={{ width: `${weekly.goal.taskProgress}%` }} /></b></div><div><span>专注 {weekly.focusedMinutes}/{weekly.goal.targetFocusMinutes} 分钟</span><b><i style={{ width: `${weekly.goal.focusProgress}%` }} /></b></div></div>
              <button disabled={weekly.goal.status !== 'READY' || busyId !== null} onClick={() => void claimWeeklyBonus()}>{weekly.goal.status === 'CLAIMED' ? '本周已领取' : weekly.goal.status === 'READY' ? '领取成长奖励' : `目标进度 ${weekly.goal.overallProgress}%`}</button>
            </div>}
            <h3 className="subheading">学科节奏</h3>
            <div className="subject-list">{weekly.subjects.length ? weekly.subjects.map((subject) => <article key={subject.subject}><strong>{subject.subject}</strong><span>完成 {subject.completedTasks} 项</span><span>预计 {subject.averageEstimatedMinutes} / 实际 {subject.averageActualMinutes} 分钟</span></article>) : <p>完成作业后，这里会出现更贴合你的时间曲线。</p>}</div>
            <h3 className="subheading">里程碑徽章</h3>
            <div className="badge-grid">{weekly.badges.map((badge) => <article className={badge.unlocked ? 'unlocked' : ''} key={badge.id}><span>{badge.icon}</span><strong>{badge.title}</strong><small>{badge.description}</small><em>{badge.unlocked ? '已解锁' : `${badge.progress}/${badge.target}`}</em></article>)}</div>
            <h3 className="subheading">家庭鼓励墙</h3>
            <div className="comment-list">{weekly.comments.length ? weekly.comments.map((item) => <article key={item.id}><strong>{item.actorName} · {item.actorRelation}</strong><p>{item.content}</p></article>) : <article><strong>嘀嘀</strong><p>还没有留言，写下这周最想夸夸自己的地方吧。</p></article>}</div>
            <div className="comment-form"><input value={comment} maxLength={240} onChange={(event) => setComment(event.target.value)} placeholder="写下我的本周总结" /><button disabled={!comment.trim() || busyId !== null} onClick={() => void addComment()}>保存总结</button></div>
          </>}
        </section>
      )}

      {tab === 'rewards' && (
        <section className="content-section">
          <div className="section-heading"><div><p>努力会闪闪发光</p><h2>星星奖励</h2></div><div className="star-balance"><Star />{rewards?.studentStars ?? 0}</div></div>
          <p className="section-intro">完成任务积累星星，可以解锁嘀嘀皮肤，也可以实现家庭小心愿。</p>
          <div className="reward-panels"><button className={rewardPanel === 'STORE' ? 'active' : ''} onClick={() => setRewardPanel('STORE')}><Gift />奖励商店</button><button className={rewardPanel === 'HISTORY' ? 'active' : ''} onClick={() => setRewardPanel('HISTORY')}><History />申请记录</button><button className={rewardPanel === 'LEDGER' ? 'active' : ''} onClick={() => setRewardPanel('LEDGER')}><WalletCards />星星账本</button></div>
          {rewardPanel === 'STORE' && <><div className="reward-filters">{([['ALL','全部奖励'],['SKIN','嘀嘀皮肤'],['WISH','家庭心愿']] as [RewardFilter,string][]).map(([id,label]) => <button className={rewardFilter === id ? 'active' : ''} key={id} onClick={() => setRewardFilter(id)}>{label}</button>)}</div>
          <div className="reward-grid">{visibleRewards.map((reward) => {
            const pending = reward.redemptionStatus === 'REQUESTED';
            return <article className={`${reward.category === 'SKIN' ? 'skin' : 'wish'} ${reward.equipped ? 'equipped' : ''}`} key={reward.id}>
              <span className="reward-icon">{reward.icon}</span>
              <div><small>{reward.category === 'SKIN' ? '嘀嘀皮肤' : '家庭心愿'} · {reward.sourceType === 'CUSTOM' ? '家人自定义' : '内置奖励'}</small><h3>{reward.name}</h3><p>{reward.requiredStars} 颗星 · {reward.createdByName}设置</p>{reward.equipped && <em>嘀嘀正在使用</em>}</div>
              {reward.category === 'SKIN' && reward.owned ? <button disabled={reward.equipped || busyId !== null} onClick={() => void equipSkin(reward.id,reward.name)}>{reward.equipped ? '使用中' : '换上皮肤'}</button> : <button
                disabled={!reward.canRedeem || pending || busyId !== null}
                onClick={() => void redeem(reward.id, reward.name)}
              >{pending ? '等待家长审批' : reward.canRedeem ? '申请兑换' : '继续攒星'}</button>}
            </article>;
          })}</div></>}
          {rewardPanel === 'HISTORY' && <div className="history-list">{rewards?.redemptions.length ? rewards.redemptions.map((item) => <article key={item.id}><span>{item.rewardIcon}</span><div><strong>{item.rewardName}</strong><p>{item.requestedAt} · {item.requiredStars} 颗星</p></div><em className={`record-${item.status.toLowerCase()}`}>{item.status === 'REQUESTED' ? '等待审批' : item.status === 'APPROVED' ? '已批准' : '已暂缓'}</em></article>) : <div className="empty-mini">还没有奖励申请记录</div>}</div>}
          {rewardPanel === 'LEDGER' && <div className="history-list ledger-list">{rewards?.starTransactions.length ? rewards.starTransactions.map((item) => <article key={item.id}><span>{item.amount > 0 ? '⭐' : '🎁'}</span><div><strong>{item.reason}</strong><p>{item.createdAt}</p></div><em className={item.amount > 0 ? 'plus' : 'minus'}>{item.amount > 0 ? '+' : ''}{item.amount}</em></article>) : <div className="empty-mini">完成第一项任务后，星星记录会出现在这里</div>}</div>}
        </section>
      )}
    </main>
  );
}
