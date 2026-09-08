'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  Award, Camera, Check, ChevronDown, ChevronUp, Clock3, Eye, Gift,
  Home, Medal, Move, Pause, Play, Plus,
  Settings2, ShoppingBag, Sparkles, Star, TimerReset, Trophy, UsersRound,
} from 'lucide-react';
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/components/prototype-dialog';
import { usePrototype } from '@/components/prototype-provider';
import { getFocusMinutes, weekChart, type HomeworkTask } from '@/lib/prototype-data';

type ChildTab = 'today' | 'growth' | 'rewards' | 'report';
type RewardFilter = 'all' | 'didi' | 'family';

const childNav: { key: ChildTab; label: string; icon: typeof Home }[] = [
  { key: 'today', label: '今日计划', icon: Home },
  { key: 'growth', label: '我的成长', icon: Trophy },
  { key: 'rewards', label: '星星商店', icon: ShoppingBag },
  { key: 'report', label: '本周回顾', icon: Award },
];

const rewardFilters: { key: RewardFilter; label: string }[] = [
  { key: 'all', label: '全部奖励' },
  { key: 'didi', label: '嘀嘀皮肤' },
  { key: 'family', label: '家庭心愿' },
];

function formatSeconds(seconds: number) {
  const minute = Math.floor(seconds / 60);
  const second = seconds % 60;
  return `${String(minute).padStart(2, '0')}:${String(second).padStart(2, '0')}`;
}

export default function ChildPage() {
  const {
    activeChild, activeChildId, rewards, setActiveChild, claimPlan, moveTask, startTask,
    pauseTask, completeTask, tickTask, handleOverrun, addRewardRequest, redeemReward,
  } = usePrototype();
  const [tab, setTab] = useState<ChildTab>('today');
  const [taskId, setTaskId] = useState<string | null>(null);
  const [taskOpen, setTaskOpen] = useState(false);
  const [overrunOpen, setOverrunOpen] = useState(false);
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [rewardOpen, setRewardOpen] = useState(false);
  const [rewardFilter, setRewardFilter] = useState<RewardFilter>('all');
  const [celebration, setCelebration] = useState(false);
  const [evidence, setEvidence] = useState(false);
  const [rewardTitle, setRewardTitle] = useState('周末去公园骑车');
  const [rewardStars, setRewardStars] = useState(70);
  const [toast, setToast] = useState('');

  const activeTask = activeChild.tasks.find((task) => task.id === taskId) ?? null;
  const homeworkTasks = activeChild.tasks.filter((task) => task.kind === 'homework');
  const done = homeworkTasks.filter((task) => task.status === 'done').length;
  const progress = Math.round(done / Math.max(1, homeworkTasks.length) * 100);
  const firstTodo = activeChild.tasks.find((task) => !['done', 'skipped'].includes(task.status));
  const runningTaskId = activeTask?.status === 'active' ? activeTask.id : null;

  useEffect(() => {
    if (!runningTaskId) return;
    const timer = window.setInterval(() => tickTask(runningTaskId), 1000);
    return () => window.clearInterval(timer);
  }, [runningTaskId, tickTask]);

  const totalFocusMinutes = useMemo(
    () => activeChild.tasks.filter((task) => task.kind === 'homework').reduce((sum, task) => sum + task.estimatedMinutes, 0),
    [activeChild.tasks],
  );
  const filteredRewards = useMemo(
    () => rewards.filter((reward) => rewardFilter === 'all' || (rewardFilter === 'family' ? reward.custom : !reward.custom)),
    [rewardFilter, rewards],
  );

  function showToast(message: string) {
    setToast(message);
    window.setTimeout(() => setToast(''), 2400);
  }

  function openTask(task: HomeworkTask) {
    setTaskId(task.id);
    setEvidence(Boolean(task.evidence));
    setTaskOpen(true);
  }

  function finishCurrentTask() {
    if (!activeTask) return;
    const isFinalHomework = homeworkTasks.filter((task) => task.status !== 'done' && task.id !== activeTask.id).length === 0;
    completeTask(activeTask.id, evidence);
    setTaskOpen(false);
    showToast(activeTask.kind === 'break' ? '休息完成，眼睛充好电啦' : '完成打卡，获得 5 颗星 ⭐');
    if (isFinalHomework) window.setTimeout(() => setCelebration(true), 450);
  }

  function makeRewardRequest() {
    addRewardRequest(rewardTitle, rewardStars);
    setRewardOpen(false);
    setRewardFilter('family');
    showToast('奖励申请已发给爸爸妈妈');
  }

  return (
    <main className="prototype-stage child-stage">
      <aside className="prototype-rail child-rail">
        <a href="/" className="rail-brand"><Clock3 /><span>作业时光</span></a>
        <div className="rail-copy">
          <span>孩子平板端</span>
          <h1>我的计划，<br />我来做主。</h1>
          <p>当前展示平板竖屏体验。先认领计划，再开始计时、打卡和收集星星。</p>
        </div>
        <div className="scenario-card">
          <strong>快速体验关键状态</strong>
          <button onClick={() => { if (!activeChild.claimed) claimPlan(); setTab('today'); showToast('计划已认领'); }}><Play />进入执行状态</button>
          <button onClick={() => { setOverrunOpen(true); }}><TimerReset />体验超时重排</button>
          <button onClick={() => setCelebration(true)}><Sparkles />查看全部完成特效</button>
        </div>
        <a href="/parent" className="rail-switch">切换到家长微信端 <span>→</span></a>
      </aside>

      <section className="device-frame tablet-frame" aria-label="作业时光孩子平板端原型">
        <div className="tablet-status"><span>17:28 · 9月8日 星期二</span><span>学习模式　Wi‑Fi　78%</span></div>
        <div className="child-app">
          <header className="tablet-header">
            <div className="tablet-brand"><span><Clock3 /></span><div><strong>作业时光</strong><small>自己规划，快乐成长</small></div></div>
            <div className="child-header-actions">
              <button className="star-balance" onClick={() => setTab('rewards')}><Star /><strong>{activeChild.stars}</strong><span>颗星星</span></button>
              <button className="child-avatar-button" onClick={() => setActiveChild(activeChildId === 'xiaoman' ? 'keke' : 'xiaoman')}><span>{activeChild.avatar}</span><div><strong>{activeChild.name}</strong><small>{activeChild.grade}</small></div><ChevronDown /></button>
            </div>
          </header>

          <div className="tablet-main">
            <nav className="tablet-sidebar" aria-label="孩子端主导航">
              {childNav.map((item) => {
                const Icon = item.icon;
                return <button key={item.key} onClick={() => setTab(item.key)} className={tab === item.key ? 'active' : ''}><Icon /><span>{item.label}</span></button>;
              })}
              <button className="tablet-settings" onClick={() => setScheduleOpen(true)}><Settings2 /><span>作息设置</span></button>
            </nav>

            <div className="tablet-content">
              {tab === 'today' && !activeChild.claimed && (
                <section className="claim-view">
                  <div className="claim-hello">
                    <div><span className="child-eyebrow"><Sparkles />嘀嘀已准备好</span><h2>{activeChild.name.slice(1)}，来认领今天的计划吧！</h2><p>我按照作业量、难度和用眼时间排好了。你可以先调整，再决定自己的节奏。</p></div>
                    <img src="/assets/didi-mascot.png" alt="嘀嘀邀请你认领今日计划" />
                  </div>
                  <div className="claim-summary">
                    <span><strong>{homeworkTasks.length}</strong> 项作业</span><span><strong>{totalFocusMinutes}</strong> 分钟专注</span><span><strong>{activeChild.plannedEnd}</strong> 预计完成</span><span><strong>55</strong> 分钟晚间时光</span>
                  </div>
                  <div className="claim-heading"><div><strong>今天这样安排</strong><small>点箭头即可调整顺序</small></div><span><Move />我可以自己调整</span></div>
                  <div className="claim-tasks">
                    {activeChild.tasks.map((task, index) => (
                      <article key={task.id} className={`claim-task ${task.kind}`}>
                        <span className="claim-index">{task.kind === 'break' ? '休' : index + 1}</span>
                        <span className="claim-emoji">{task.icon}</span>
                        <div><small>{task.start}–{task.end} · {task.subject}</small><strong>{task.title}</strong><p>{task.difficulty} · {task.estimatedMinutes} 分钟 {task.eyeLoad === 'high' ? '· 近距离用眼' : ''}</p></div>
                        <div className="claim-reorder"><button disabled={index === 0} onClick={() => moveTask(task.id, -1)}><ChevronUp /></button><button disabled={index === activeChild.tasks.length - 1} onClick={() => moveTask(task.id, 1)}><ChevronDown /></button></div>
                      </article>
                    ))}
                  </div>
                  <div className="why-order"><span>💡</span><p><strong>为什么这样排？</strong>先用短朗读热身，再趁精力好挑战数学；在完整任务间休息，最后用观察和整理轻松收尾。</p></div>
                  <button className="claim-button" onClick={() => { claimPlan(); showToast('这是你的今日计划，出发吧！'); }}><Sparkles />这是我的今日计划</button>
                </section>
              )}

              {tab === 'today' && activeChild.claimed && (
                <section className="child-today-view">
                  <div className="child-welcome">
                    <div><span>下午好，{activeChild.name.slice(1)}</span><h2>{done === homeworkTasks.length ? '今天全部完成啦！' : '一步一步，按自己的节奏来'}</h2><p>{done === 0 ? '先完成一个短热身，接着挑战今天最需要专注的任务。' : `已经完成 ${done} 项，嘀嘀一直陪着你。`}</p></div>
                    <button onClick={() => setScheduleOpen(true)}><Settings2 />调整今天</button>
                  </div>
                  <section className="child-progress-card">
                    <div className="progress-ring" style={{ '--progress': `${progress * 3.6}deg` } as React.CSSProperties}><span><strong>{progress}%</strong><small>今日进度</small></span></div>
                    <div className="progress-details"><span className="child-eyebrow"><Sparkles />嘀嘀的实时预估</span><h3>预计 {activeChild.plannedEnd} 完成</h3><p>{activeChild.plannedEnd === activeChild.originalEnd ? '计划进行得刚刚好，完成后还有 55 分钟晚间时光。' : `原计划 ${activeChild.originalEnd} 完成，已根据实际进度自动更新。`}</p><div><span>🔥 连胜 {activeChild.streak} 天</span><span>⭐ 今天 +{done * 5}</span></div></div>
                    <img src="/assets/didi-mascot.png" alt="嘀嘀正在陪伴计划" />
                  </section>

                  <div className="child-section-title"><div><strong>今天的时间线</strong><small>一次只专心做好一件事</small></div><button onClick={() => showToast('长按任务即可拖动调整')}><Move />调整顺序</button></div>
                  <div className="child-timeline">
                    {activeChild.tasks.map((task) => (
                      <article key={task.id} className={`child-task-card ${task.kind} ${task.status} ${task.id === firstTodo?.id ? 'next' : ''}`}>
                        <div className="child-task-time"><strong>{task.start}</strong><small>{task.end}</small></div>
                        <span className="child-task-emoji">{task.status === 'done' ? <Check /> : task.icon}</span>
                        <div className="child-task-copy"><span>{task.subject} · {task.type}</span><strong>{task.title}</strong><p>{task.estimatedMinutes} 分钟 · {task.difficulty}{task.confidence === 'low' ? ' · 待确认' : ''}</p>{task.needsHelp && <em>已向家长求助，将在最后再试试</em>}</div>
                        <button className="child-task-action" disabled={task.status === 'done'} onClick={() => openTask(task)}>{task.status === 'done' ? '完成' : task.status === 'active' ? '继续' : task.kind === 'break' ? '休息' : '开始'}</button>
                      </article>
                    ))}
                  </div>
                  <button className="child-add-task" onClick={() => showToast('可以手动添加临时任务或学科')}><Plus />我还想添加一项</button>
                </section>
              )}

              {tab === 'growth' && (
                <section className="growth-view">
                  <div className="tablet-page-title"><div><span>我的成长</span><h2>每一次自主选择，都算数</h2></div><button><Award />全部徽章</button></div>
                  <section className="growth-hero">
                    <div className="growth-copy"><span>本月成长等级</span><h3>小小规划师 · Lv. 4</h3><p>再自主完成 3 天，就能升级为“节奏掌控者”！</p><div className="level-track"><i /></div><small>7 / 10 天</small></div>
                    <div className="mascot-pedestal"><i /><img src="/assets/didi-mascot.png" alt="嘀嘀当前皮肤" /><span>薄荷森林皮肤</span></div>
                  </section>
                  <div className="child-section-title"><div><strong>嘀嘀的衣橱</strong><small>坚持打卡，解锁不同伙伴皮肤</small></div><span className="collection-count">2 / 6</span></div>
                  <div className="skin-grid">
                    <article className="skin-card selected"><div><img src="/assets/didi-mascot.png" alt="薄荷森林嘀嘀" /></div><strong>薄荷森林</strong><small><Check />正在使用</small></article>
                    <article className="skin-card"><div className="sunset-skin"><img src="/assets/didi-mascot.png" alt="暖阳嘀嘀" /></div><strong>暖阳活力</strong><small>⭐ 60 星解锁</small></article>
                    <article className="skin-card locked"><div><img src="/assets/didi-mascot.png" alt="星河嘀嘀待解锁" /><span>🔒</span></div><strong>星河漫游</strong><small>连续 14 天解锁</small></article>
                  </div>
                  <div className="child-section-title"><div><strong>里程碑徽章</strong><small>看看这周新获得了什么</small></div></div>
                  <div className="badge-row">
                    {[['🧭','自主启程','首次认领计划'],['🔥','七日连胜','连续完成 7 天'],['🌿','护眼达人','认真休息 20 次'],['🧠','勇敢挑战','完成 5 项难题']].map((badge, index) => <article className={index === 3 ? 'new' : ''} key={badge[1]}><span>{badge[0]}</span><strong>{badge[1]}</strong><small>{badge[2]}</small>{index === 3 && <b>NEW</b>}</article>)}
                  </div>
                </section>
              )}

              {tab === 'rewards' && (
                <section className="rewards-view">
                  <div className="tablet-page-title"><div><span>星星商店</span><h2>把坚持换成期待</h2></div><button className="new-reward-button" onClick={() => setRewardOpen(true)}><Plus />许一个新愿望</button></div>
                  <section className="star-wallet"><div><Star /><span><small>我的星星</small><strong>{activeChild.stars}</strong></span></div><p>完成任务、认真休息和自主规划都能获得星星。</p><span>本周已获得 36 颗</span></section>
                  <div className="reward-tabs" role="tablist" aria-label="奖励分类">
                    {rewardFilters.map((filter) => <button key={filter.key} type="button" role="tab" aria-selected={rewardFilter === filter.key} className={rewardFilter === filter.key ? 'active' : ''} onClick={() => setRewardFilter(filter.key)}>{filter.label}</button>)}
                  </div>
                  <div className="reward-grid" role="tabpanel" aria-live="polite">
                    {filteredRewards.map((reward) => (
                      <article className={`reward-card ${reward.status}`} key={reward.id}>
                        <span className="reward-illustration">{reward.icon}</span>
                        <div><small>{reward.custom ? '家庭自定义' : '嘀嘀限定'}</small><strong>{reward.title}</strong></div>
                        <button disabled={reward.status !== 'available'} onClick={() => showToast(redeemReward(reward.id) ? `兑换成功！已通知爸爸妈妈` : '星星还差一点，再坚持一下吧')}>{reward.status === 'pending' ? '等待家长确认' : reward.status === 'locked' ? '尚未解锁' : <><Star />{reward.stars} 兑换</>}</button>
                      </article>
                    ))}
                  </div>
                  <section className="reward-tip"><img src="/assets/didi-mascot.png" alt="嘀嘀奖励提示" /><p><strong>想要新的奖励？</strong>你可以自己设置愿望和星星门槛，爸爸妈妈确认后就会出现在这里。</p><button onClick={() => setRewardOpen(true)}>去设置</button></section>
                </section>
              )}

              {tab === 'report' && (
                <section className="child-report-view">
                  <div className="tablet-page-title"><div><span>本周回顾</span><h2>这一周，我更会规划了</h2></div><span className="shared-pill"><UsersRound />和家长一起看</span></div>
                  <section className="child-week-hero"><div><span>本周自主完成率</span><strong>92%</strong><p>比上周进步了 8%，最明显的进步是“主动调整顺序”。</p></div><img src="/assets/didi-mascot.png" alt="嘀嘀为本周进步庆祝" /><div className="week-stars"><Star />本周 +36</div></section>
                  <div className="child-week-stats"><article><span>🔥</span><p><strong>{activeChild.streak} 天</strong><small>连续完成</small></p></article><article><span>🎯</span><p><strong>6 次</strong><small>自主调整</small></p></article><article><span>⏱️</span><p><strong>6 分钟</strong><small>平均估时误差</small></p></article></div>
                  <section className="child-chart-card">
                    <div><strong>每天用了多久？</strong><span><i />预估　<i />实际</span></div>
                    <div className="child-bars">{weekChart.map((item) => <div key={item.day}><span><i style={{ height: `${item.estimated * .75}px` }} /><i style={{ height: `${item.actual * .75}px` }} /></span><small>周{item.day}</small></div>)}</div>
                  </section>
                  <section className="parent-encouragement"><span className="parent-note-avatar">妈</span><div><small>妈妈的鼓励 · 今天 16:50</small><p>“这周你越来越会自己安排顺序了，特别棒！周末我们一起去公园吧。”</p></div><span>💚</span></section>
                  <section className="next-week-card"><span>🌱</span><div><strong>下周的小目标</strong><p>数学练习开始前先读清题目，争取让估时更准确。</p></div><button onClick={() => showToast('目标已设为下周提醒')}>接受挑战</button></section>
                </section>
              )}
            </div>
          </div>
        </div>
      </section>

      {toast && <div className="prototype-toast child-toast"><Check />{toast}</div>}

      <Dialog open={taskOpen} onOpenChange={setTaskOpen}>
        <DialogContent className="task-focus-dialog">
          {activeTask && (
            <>
              <DialogHeader><DialogTitle>{activeTask.kind === 'break' ? '让眼睛和身体休息一下' : activeTask.title}</DialogTitle><DialogDescription>{activeTask.subject} · {activeTask.type} · 预计 {activeTask.estimatedMinutes} 分钟</DialogDescription></DialogHeader>
              <div className={`focus-hero ${activeTask.kind}`}>
                <img src="/assets/didi-mascot.png" alt="嘀嘀陪伴计时" />
                <span className="focus-emoji">{activeTask.icon}</span>
                <div className="focus-clock"><strong>{formatSeconds(activeTask.actualSeconds)}</strong><small>{activeTask.status === 'active' ? '正在计时' : activeTask.status === 'paused' ? '已暂停' : '准备好就开始'}</small></div>
              </div>
              {activeTask.kind === 'break' ? (
                <div className="break-suggestions"><span><Eye />看看 6 米外</span><span>💧 喝口水</span><span>🧘 伸伸懒腰</span><p>休息时先放下平板，嘀嘀会用声音提醒你回来。</p></div>
              ) : (
                <div className="focus-hints"><span>建议在 <strong>{activeTask.end}</strong> 前完成</span><span className={`focus-difficulty ${activeTask.difficulty}`}>{activeTask.difficulty}</span></div>
              )}
              <div className="timer-actions">
                {activeTask.status === 'active' ? <button className="pause-button" onClick={() => pauseTask(activeTask.id)}><Pause />先暂停</button> : <button className="play-button" onClick={() => startTask(activeTask.id)}><Play />{activeTask.actualSeconds > 0 ? '继续' : '开始计时'}</button>}
                <button className="done-button" onClick={finishCurrentTask}><Check />完成打卡</button>
              </div>
              {activeTask.kind !== 'break' && <button className={`evidence-button ${evidence ? 'uploaded' : ''}`} onClick={() => { setEvidence(true); showToast('成果照片已添加到本次任务'); }}>{evidence ? <Check /> : <Camera />}{evidence ? '成果照片已上传' : '先拍照上传成果（可选）'}</button>}
              {activeTask.kind !== 'break' && <button className="overrun-demo" onClick={() => { setTaskOpen(false); setOverrunOpen(true); }}>体验“用时超出预估”</button>}
            </>
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={overrunOpen} onOpenChange={setOverrunOpen}>
        <DialogContent className="overrun-dialog" showCloseButton={false}>
          <div className="overrun-mascot"><img src="/assets/didi-mascot.png" alt="嘀嘀提醒任务超时" /><span>还好吗？</span></div>
          <DialogHeader><DialogTitle>这项任务比预计多用了 10 分钟</DialogTitle><DialogDescription>没关系，困难任务需要更多时间很正常。请选择接下来怎么做，嘀嘀会重排剩余计划。</DialogDescription></DialogHeader>
          <div className="time-shift"><span><small>原预计完成</small><strong>{activeChild.originalEnd}</strong></span><i>→</i><span><small>更新后预计</small><strong>{activeChild.plannedEnd === activeChild.originalEnd ? '20:05' : activeChild.plannedEnd}</strong></span></div>
          <button className="decision-card challenge" onClick={() => { handleOverrun(taskId ?? firstTodo?.id ?? activeChild.tasks[0].id, 'challenge'); setOverrunOpen(false); showToast('继续挑战！剩余计划已更新'); }}><span>💪</span><p><strong>继续挑战</strong><small>我有信心完成，顺延后面的安排</small></p><ChevronDown /></button>
          <button className="decision-card skip" onClick={() => { handleOverrun(taskId ?? firstTodo?.id ?? activeChild.tasks[0].id, 'skip'); setOverrunOpen(false); showToast('已移到最后，并通知家长来帮忙'); }}><span>🛟</span><p><strong>暂时跳过</strong><small>先做下一项，把它移到最后并求助家长</small></p><ChevronDown /></button>
          <p className="overrun-note">无论选择哪一个，必要的护眼休息都不会被压缩。</p>
        </DialogContent>
      </Dialog>

      <Dialog open={scheduleOpen} onOpenChange={setScheduleOpen}>
        <DialogContent className="child-schedule-dialog">
          <DialogHeader><DialogTitle>调整今天的时间</DialogTitle><DialogDescription>固定作息来自家庭设置，你可以为今天添加例外，家长会实时看到变化。</DialogDescription></DialogHeader>
          <div className="schedule-date"><button>‹</button><strong>9 月 8 日 · 星期二</strong><button>›</button></div>
          <div className="child-schedule-list"><div><span>🏠</span><p><small>到家时间</small><strong>17:20</strong></p><button>修改</button></div><div className="special"><span>🏀</span><p><small>兴趣班 · 今天例外</small><strong>20:00–21:00</strong></p><button>修改</button></div><div><span>🌙</span><p><small>准备睡觉</small><strong>21:30</strong></p><button>修改</button></div></div>
          <div className="eye-settings"><Eye /><p><strong>{activeChild.grade}默认专注 {getFocusMinutes(activeChild.grade)} 分钟</strong><small>完整休息 10 分钟 · 规则可和家长一起调整</small></p><button>调整</button></div>
          <button className="save-schedule" onClick={() => { setScheduleOpen(false); showToast('今天的作息已更新，并同步给家长'); }}>保存并重新安排今天</button>
        </DialogContent>
      </Dialog>

      <Dialog open={rewardOpen} onOpenChange={setRewardOpen}>
        <DialogContent className="reward-request-dialog">
          <DialogHeader><DialogTitle>许一个新的奖励愿望</DialogTitle><DialogDescription>你可以自己想奖励和星星门槛，爸爸妈妈确认后才会生效。</DialogDescription></DialogHeader>
          <label><span>我想要</span><input value={rewardTitle} onChange={(event) => setRewardTitle(event.target.value)} /></label>
          <fieldset className="reward-field"><legend>需要多少颗星星？</legend><div className="star-stepper"><button type="button" onClick={() => setRewardStars(Math.max(10, rewardStars - 10))}>−</button><strong><Star />{rewardStars}</strong><button type="button" onClick={() => setRewardStars(rewardStars + 10)}>＋</button></div></fieldset>
          <div className="approval-flow"><span>我提出愿望</span><i>→</i><span>家长确认</span><i>→</i><span>攒星兑换</span></div>
          <button className="send-reward-request" onClick={makeRewardRequest}><Gift />发给爸爸妈妈确认</button>
        </DialogContent>
      </Dialog>

      <Dialog open={celebration} onOpenChange={setCelebration}>
        <DialogContent className="celebration-dialog" showCloseButton={false}>
          <div className="spark-field" aria-hidden="true">{Array.from({ length: 12 }).map((_, index) => <i key={index}>✦</i>)}</div>
          <span className="celebration-label">今日全部完成</span>
          <img src="/assets/didi-mascot.png" alt="嘀嘀解锁闪光特效" />
          <h2>太棒了，{activeChild.name.slice(1)}！</h2><p>你完成了自己的全部计划，嘀嘀解锁了今日专属闪光特效。</p>
          <div className="celebration-rewards"><span><Star />+25 星星</span><span><Medal />连胜 {activeChild.streak + 1} 天</span></div>
          <button onClick={() => { setCelebration(false); setTab('growth'); }}>去看看嘀嘀的新成长</button>
        </DialogContent>
      </Dialog>
    </main>
  );
}
