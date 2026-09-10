'use client';

import { useMemo, useState } from 'react';
import {
  Bell, Camera, Check, ChevronDown, ChevronUp, Clock3, Edit3, Gift,
  Home, MessageCircleHeart, MoreHorizontal, Plus, QrCode, RefreshCcw,
  ScanLine, Settings2, Sparkles, Star, TrendingUp, UserRound, UsersRound, WandSparkles,
} from 'lucide-react';
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/components/prototype-dialog';
import { usePrototype } from '@/components/prototype-provider';
import { getFocusMinutes, weekChart } from '@/lib/prototype-data';

type ParentTab = 'today' | 'report' | 'family' | 'profile';
type ScanStage = 'choose' | 'scanning' | 'result';

const navItems: { key: ParentTab; label: string; icon: typeof Home }[] = [
  { key: 'today', label: '今天', icon: Home },
  { key: 'report', label: '周报', icon: TrendingUp },
  { key: 'family', label: '家庭', icon: UsersRound },
  { key: 'profile', label: '我的', icon: UserRound },
];

export default function ParentPage() {
  const {
    activeChild, activeChildId, children, activities, rewards, setActiveChild, moveTask,
    approveReward, resetDemo,
  } = usePrototype();
  const [tab, setTab] = useState<ParentTab>('today');
  const [scanOpen, setScanOpen] = useState(false);
  const [scanStage, setScanStage] = useState<ScanStage>('choose');
  const [familyOpen, setFamilyOpen] = useState(false);
  const [comment, setComment] = useState('这周你越来越会自己安排顺序了，特别棒！');
  const [toast, setToast] = useState('');

  const homeworkTasks = activeChild.tasks.filter((task) => task.kind === 'homework');
  const done = homeworkTasks.filter((task) => task.status === 'done').length;
  const filteredActivities = activities.filter((item) => item.childId === activeChildId).slice(0, 4);
  const pendingRewards = rewards.filter((reward) => reward.status === 'pending');

  const estimatedTotal = useMemo(
    () => activeChild.tasks.reduce((sum, task) => sum + task.estimatedMinutes, 0),
    [activeChild.tasks],
  );

  function showToast(message: string) {
    setToast(message);
    window.setTimeout(() => setToast(''), 2600);
  }

  function startScan() {
    setScanStage('scanning');
    window.setTimeout(() => setScanStage('result'), 1500);
  }

  function closeScanWithPlan() {
    setScanOpen(false);
    setScanStage('choose');
    showToast('嘀嘀已生成计划，并同步到学生端');
  }

  return (
    <main className="prototype-stage parent-stage">
      <aside className="prototype-rail">
        <a href="/" className="rail-brand"><Clock3 /><span>作业时光</span></a>
        <div className="rail-copy">
          <span>家长微信端</span>
          <h1>安心看见成长，<br />把安排交给孩子。</h1>
          <p>当前展示 390 × 844 手机竖屏体验。所有操作会保存到这台设备。</p>
        </div>
        <div className="rail-family-card">
          <p>林家 · 今日动态</p>
          <strong>{activities.length} 条亲子协作记录</strong>
          <span>孩子的每次自主调整，家长都能看见。</span>
        </div>
        <a href="/child" className="rail-switch">切换到孩子平板端 <span>→</span></a>
      </aside>

      <section className="device-frame phone-frame" aria-label="作业时光家长端原型">
        <div className="phone-status"><span>17:18</span><span>● 〽︎ 78%</span></div>
        <div className="parent-app">
          <header className="mobile-header">
            <div className="mobile-brand"><span className="tiny-logo"><Clock3 /></span><strong>作业时光</strong></div>
            <button className="icon-button" aria-label="通知"><Bell size={19} /><i /></button>
          </header>

          <div className="child-switcher" aria-label="切换孩子">
            {children.map((child) => (
              <button
                key={child.id}
                className={`child-chip ${child.id === activeChildId ? 'active' : ''} ${child.theme}`}
                onClick={() => setActiveChild(child.id)}
              >
                <span>{child.avatar}</span><b>{child.name.slice(1)}</b><small>{child.grade}</small>
              </button>
            ))}
            <button className="add-child-chip" onClick={() => setFamilyOpen(true)} aria-label="添加孩子"><Plus /></button>
          </div>

          <div className="parent-scroll">
            {tab === 'today' && (
              <section className="mobile-view">
                <div className="greeting-row">
                  <div><p>9 月 8 日 · 星期二</p><h2>{activeChild.name.slice(1)}今天状态不错呀</h2></div>
                  <span className="weather-dot">☀️</span>
                </div>

                <section className="plan-overview-card">
                  <div className="overview-head">
                    <span className="overview-icon"><Sparkles /></span>
                    <div><p>嘀嘀的今日计划</p><h3>{done === homeworkTasks.length ? '今天全部完成啦！' : `预计 ${activeChild.plannedEnd} 完成`}</h3></div>
                    <button aria-label="更多"><MoreHorizontal /></button>
                  </div>
                  <div className="overview-progress"><span style={{ width: `${Math.max(8, done / homeworkTasks.length * 100)}%` }} /></div>
                  <div className="overview-meta">
                    <span><strong>{homeworkTasks.length}</strong> 项作业</span>
                    <span><strong>{estimatedTotal}</strong> 分钟含休息</span>
                    <span><strong>{activeChild.bedtime}</strong> 前睡觉</span>
                  </div>
                  <div className="sleep-buffer"><span>🌙</span><p><strong>已留出 55 分钟晚间时光</strong><small>阅读、洗漱和放松都安排好啦</small></p></div>
                </section>

                <div className="quick-actions">
                  <button className="scan-action" onClick={() => { setScanStage('choose'); setScanOpen(true); }}>
                    <span><Camera /></span><div><strong>拍照录作业</strong><small>嘀嘀帮你识别并排好</small></div><WandSparkles />
                  </button>
                  <button onClick={() => showToast('已打开手动作业录入')}><Edit3 /><span>手动录入</span></button>
                </div>

                <div className="section-heading"><div><span>今日时间线</span><small>长按拖动也可以调整</small></div><button onClick={() => showToast('已按最新作息重新规划')}><RefreshCcw />重排</button></div>
                <div className="parent-timeline">
                  {activeChild.tasks.map((task, index) => (
                    <article className={`parent-task ${task.status} ${task.kind}`} key={task.id}>
                      <time>{task.start}</time>
                      <span className="timeline-node" />
                      <div className="task-card-body">
                        <div className="task-card-title"><span className="task-emoji">{task.icon}</span><div><small>{task.subject} · {task.type}</small><strong>{task.title}</strong></div></div>
                        <div className="task-card-meta">
                          <span>{task.estimatedMinutes} 分钟</span><span className={`difficulty ${task.difficulty}`}>{task.difficulty}</span>
                          {task.confidence === 'low' && <span className="pending-check">待确认</span>}
                          {task.status === 'done' && <span className="done-check"><Check />已完成</span>}
                        </div>
                        {task.needsHelp && <p className="help-note">需要帮助 · 已移到计划末尾</p>}
                        <div className="reorder-buttons">
                          <button disabled={index === 0} onClick={() => moveTask(task.id, -1, 'parent')} aria-label="上移"><ChevronUp /></button>
                          <button disabled={index === activeChild.tasks.length - 1} onClick={() => moveTask(task.id, 1, 'parent')} aria-label="下移"><ChevronDown /></button>
                        </div>
                      </div>
                    </article>
                  ))}
                </div>

                <div className="section-heading activity-heading"><div><span>协作记录</span><small>谁调整了什么，一目了然</small></div><button onClick={() => showToast('已展示全部记录')}>全部</button></div>
                <div className="activity-list">
                  {filteredActivities.map((item) => (
                    <div className="activity-item" key={item.id}><span className={`activity-avatar ${item.tone}`}>{item.actor.slice(-1)}</span><p><strong>{item.actor}</strong>{item.action}<small>{item.time} · {item.relation}</small></p></div>
                  ))}
                </div>
              </section>
            )}

            {tab === 'report' && (
              <section className="mobile-view report-view">
                <div className="page-title-row"><div><p>9 月 2 日—9 月 8 日</p><h2>{activeChild.name.slice(1)}的成长周报</h2></div><span className="week-medal">🏅</span></div>
                <section className="report-hero">
                  <div><span>本周自主完成率</span><strong>92<small>%</small></strong><p>比上周进步 8%</p></div>
                  <img src="/assets/didi-mascot.png" alt="嘀嘀机器人" />
                </section>
                <div className="report-metrics">
                  <article><span>🔥</span><strong>{activeChild.streak} 天</strong><small>连续完成</small></article>
                  <article><span>⭐</span><strong>+36</strong><small>本周星星</small></article>
                  <article><span>🧩</span><strong>6 次</strong><small>自主调整</small></article>
                </div>
                <section className="chart-card">
                  <div className="chart-head"><div><strong>预估与实际用时</strong><small>嘀嘀正在越来越懂{activeChild.name.slice(1)}</small></div><span>本周</span></div>
                  <div className="bar-chart">
                    {weekChart.map((item) => (
                      <div className="bar-group" key={item.day}>
                        <div className="bars"><i style={{ height: `${item.estimated * .62}px` }} /><i style={{ height: `${item.actual * .62}px` }} /></div><span>{item.day}</span>
                      </div>
                    ))}
                  </div>
                  <div className="chart-legend"><span><i className="estimated" />预估</span><span><i className="actual" />实际</span><strong>平均误差 6 分钟</strong></div>
                </section>
                <section className="insight-card"><span className="insight-icon">💡</span><div><strong>嘀嘀发现</strong><p>数学书写平均比预估多 7 分钟；英语跟读完成得更快。下周会自动微调。</p></div></section>
                <section className="comment-card">
                  <div className="comment-title"><MessageCircleHeart /><strong>给孩子一句鼓励</strong><span>亲子共享</span></div>
                  <textarea value={comment} onChange={(event) => setComment(event.target.value)} maxLength={60} />
                  <div><span>{comment.length}/60</span><button onClick={() => showToast('鼓励已送达学生端 💚')}>送出鼓励</button></div>
                </section>
                <button className="weekly-bonus" onClick={() => showToast('已发放 10 颗周结算星星')}><Gift /><span><strong>发放本周额外奖励</strong><small>推荐：10 颗星星</small></span><Star /></button>
              </section>
            )}

            {tab === 'family' && (
              <section className="mobile-view family-view">
                <div className="page-title-row"><div><p>共同陪伴，也尊重独立</p><h2>林家的成员与作息</h2></div><button className="round-add" onClick={() => setFamilyOpen(true)}><Plus /></button></div>
                <section className="family-card">
                  <div className="family-card-title"><strong>家庭成员</strong><span>4 人</span></div>
                  {[['妈妈','主要照护人','妈','mint'],['爸爸','共同照护人','爸','blue'],['林小满','三年级 · 平板已绑定','满','orange'],['林可可','一年级 · 平板已绑定','可','pink']].map((member) => (
                    <div className="member-row" key={member[0]}><span className={`member-avatar ${member[3]}`}>{member[2]}</span><p><strong>{member[0]}</strong><small>{member[1]}</small></p><button><Settings2 /></button></div>
                  ))}
                  <button className="family-outline-button" onClick={() => setFamilyOpen(true)}><QrCode />邀请家人或绑定孩子设备</button>
                </section>
                <section className="routine-card">
                  <div className="family-card-title"><strong>本周固定作息</strong><button onClick={() => showToast('已进入作息编辑')}>编辑</button></div>
                  <div className="weekday-row">{['一','二','三','四','五','六','日'].map((day, index) => <span className={index < 5 ? 'active' : ''} key={day}>{day}</span>)}</div>
                  <div className="routine-item"><i className="home-dot" /><time>17:20</time><p><strong>到家与点心时间</strong><small>周一至周五</small></p></div>
                  <div className="routine-item exception"><i /><time>20:00</time><p><strong>今天 · 篮球兴趣班</strong><small>临时调整，和平时不同</small></p><span>例外</span></div>
                  <div className="routine-item"><i className="sleep-dot" /><time>21:30</time><p><strong>准备睡觉</strong><small>睡前预留 55 分钟</small></p></div>
                  <button className="add-exception" onClick={() => showToast('已打开当天例外设置')}><Plus />添加当天临时安排</button>
                </section>
                <section className="eye-rule-card"><span>👀</span><div><strong>{activeChild.grade}健康用眼规则</strong><p>连续近距离学习 {getFocusMinutes(activeChild.grade)} 分钟，完整休息 10 分钟；每 20 分钟远眺 20 秒。</p></div><button onClick={() => showToast('家长与孩子都可以自定义')}>调整</button></section>
              </section>
            )}

            {tab === 'profile' && (
              <section className="mobile-view profile-view">
                <div className="profile-head"><span className="parent-avatar">妈</span><div><h2>林妈妈</h2><p>林家 · 妈妈</p></div><button><Settings2 /></button></div>
                {pendingRewards.length > 0 && (
                  <section className="approval-card">
                    <div className="family-card-title"><strong>待审批奖励</strong><span>{pendingRewards.length}</span></div>
                    {pendingRewards.map((reward) => <div className="approval-row" key={reward.id}><span>{reward.icon}</span><p><strong>{reward.title}</strong><small>孩子设置 · {reward.stars} 颗星</small></p><button onClick={() => { approveReward(reward.id); showToast('奖励已批准'); }}>批准</button></div>)}
                  </section>
                )}
                <section className="settings-list">
                  <button><span>🎁</span><p><strong>家庭奖励商店</strong><small>{rewards.filter((r) => r.status === 'available').length} 个可兑换奖励</small></p><b>›</b></button>
                  <button><span>🔔</span><p><strong>重要事件通知</strong><small>完成节点、超时与大幅调整</small></p><b>›</b></button>
                  <button><span>🎨</span><p><strong>嘀嘀主题与皮肤</strong><small>薄荷森林主题</small></p><b>›</b></button>
                  <button><span>🛡️</span><p><strong>隐私与照片管理</strong><small>演示照片仅保存在本机</small></p><b>›</b></button>
                </section>
                <div className="profile-actions"><a href="/">返回体验入口</a><button onClick={() => { resetDemo(); showToast('演示数据已重置'); }}>重置全部演示数据</button></div>
                <p className="version-copy">作业时光 · 高保真体验版 0.1</p>
              </section>
            )}
          </div>

          <nav className="mobile-tabbar" aria-label="家长端主导航">
            {navItems.map((item) => {
              const Icon = item.icon;
              return <button key={item.key} className={tab === item.key ? 'active' : ''} onClick={() => setTab(item.key)}><Icon /><span>{item.label}</span>{item.key === 'profile' && pendingRewards.length > 0 && <i />}</button>;
            })}
          </nav>
        </div>
      </section>

      {toast && <div className="prototype-toast"><Check />{toast}</div>}

      <Dialog open={scanOpen} onOpenChange={setScanOpen}>
        <DialogContent className="scan-dialog" showCloseButton={scanStage !== 'scanning'}>
          {scanStage === 'choose' && (
            <>
              <DialogHeader><DialogTitle>录入今天的作业</DialogTitle><DialogDescription>选择一张预置示例，体验嘀嘀识别并自动排期。</DialogDescription></DialogHeader>
              <button className="sample-photo-card" onClick={startScan}>
                <img className="sample-board-image" src="/assets/sample-homework-board.png" alt="老师在黑板上写下的作业示例" />
                <div><strong>老师的白板照片</strong><small>4 个学科 · 推荐体验</small></div><ScanLine />
              </button>
              <button className="sample-photo-card secondary" onClick={startScan}><span className="chat-shot">班级群<br />今日作业 📌</span><div><strong>家长群作业截图</strong><small>模拟群消息识别</small></div><ScanLine /></button>
              <button className="manual-entry"><Plus />手动录入作业</button>
            </>
          )}
          {scanStage === 'scanning' && (
            <div className="scanning-state">
              <div className="scan-visual"><ScanLine /><i /></div>
              <img src="/assets/didi-mascot.png" alt="嘀嘀正在识别作业" />
              <h3>嘀嘀正在读作业...</h3><p>识别学科、任务类型和作业量</p>
              <div className="scan-steps"><span className="done"><Check />识别文字</span><span className="active"><Sparkles />评估难度</span><span><Clock3 />生成计划</span></div>
            </div>
          )}
          {scanStage === 'result' && (
            <div className="scan-result">
              <DialogHeader><DialogTitle>计划已经排好啦</DialogTitle><DialogDescription>共识别 4 个学科、5 项任务，并安排了 1 次完整休息。</DialogDescription></DialogHeader>
              <div className="result-summary"><span><strong>17:30</strong>开始</span><i>→</i><span><strong>19:35</strong>完成</span><i>＋</i><span><strong>55 分钟</strong>晚间时光</span></div>
              <div className="recognized-list">
                {[['📖','语文','朗读《荷花》两遍','12 分钟','轻松'],['✏️','数学','练习册第 32–33 页','30 分钟','挑战'],['📝','语文','生字本第 12 页','22 分钟','适中'],['🎧','英语','Unit 2 单词跟读 3 遍','15 分钟','轻松'],['🔍','科学','观察一株植物并记录','20 分钟','待确认']].map((item) => (
                  <div key={item[2]}><span>{item[0]}</span><p><small>{item[1]}</small><strong>{item[2]}</strong></p><time>{item[3]}</time><b className={item[4] === '待确认' ? 'warning' : ''}>{item[4]}</b></div>
                ))}
              </div>
              <p className="confidence-note">⚠️ 科学作业图片略模糊，已先排入计划并标记待确认，可稍后手动修改。</p>
              <button className="generate-plan-button" onClick={closeScanWithPlan}><Sparkles />采用计划并同步给{activeChild.name.slice(1)}</button>
              <button className="text-button" onClick={() => showToast('你可以直接调整每项任务')}>先调整一下</button>
            </div>
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={familyOpen} onOpenChange={setFamilyOpen}>
        <DialogContent className="family-dialog">
          <DialogHeader><DialogTitle>邀请家人或绑定设备</DialogTitle><DialogDescription>家长微信扫码后选择家庭关系；孩子平板由家长扫码绑定。</DialogDescription></DialogHeader>
          <div className="mock-qr" aria-label="演示二维码"><QrCode /></div>
          <div className="relation-pills"><span>妈妈</span><span>爸爸</span><span>其他照护人</span></div>
          <p className="qr-hint">此二维码为原型演示，不会连接真实微信账号</p>
        </DialogContent>
      </Dialog>
    </main>
  );
}
