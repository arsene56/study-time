import { ArrowRight, BellRing, Clock3, ScanLine, Sparkles, UsersRound } from 'lucide-react';

const tasks = [
  { subject: '语文', detail: '朗读《荷花》并完成生字练习', time: '17:40', tone: 'mint' },
  { subject: '数学', detail: '练习册第 32–33 页', time: '18:15', tone: 'orange' },
  { subject: '英语', detail: 'Unit 2 单词跟读', time: '19:05', tone: 'blue' },
];

export default function Home() {
  return (
    <main className="launch-page">
      <section className="launch-shell">
        <header className="brand-row">
          <div className="brand-mark" aria-hidden="true">
            <Clock3 size={25} strokeWidth={2.4} />
          </div>
          <div>
            <p className="brand-name">作业时光</p>
            <p className="brand-slogan">自己规划，快乐成长</p>
          </div>
          <span className="prototype-badge">高保真体验版</span>
        </header>

        <div className="launch-grid">
          <section className="launch-copy">
            <div className="eyebrow"><Sparkles size={15} /> 今天也和嘀嘀一起出发</div>
            <h1>把作业安排好，<br /><span>把快乐还给晚上。</span></h1>
            <p className="launch-intro">
              从一张作业照片开始，自动识别、估时和安排休息。家长安心看进度，学生主动完成自己的计划。
            </p>

            <div className="role-actions">
              <a className="onboarding-entry" href="/onboarding">
                <span><UsersRound /></span>
                <span><strong>从首次使用开始体验</strong><small>建立家庭 · 添加学生 · 扫码绑定设备</small></span>
                <ArrowRight size={20} />
              </a>
              <a className="role-card role-card-primary" href="/parent">
                <span className="role-icon"><ScanLine /></span>
                <span><strong>我是家长</strong><small>录作业 · 看进度 · 给鼓励</small></span>
                <ArrowRight size={20} />
              </a>
              <a className="role-card" href="/child">
                <span className="role-icon child-role"><Sparkles /></span>
                <span><strong>我是学生</strong><small>认领计划 · 专注打卡 · 收集星星</small></span>
                <ArrowRight size={20} />
              </a>
            </div>

            <p className="demo-note"><BellRing size={14} /> 演示家庭：林家 · 两名学生 · 数据互相隔离</p>
          </section>

          <section className="today-preview" aria-label="今日计划预览">
            <div className="preview-top">
              <div>
                <p>林小满 · 三年级</p>
                <h2>今天的放学计划</h2>
              </div>
              <span className="streak-pill">🔥 连胜 7 天</span>
            </div>

            <div className="progress-copy"><span>已完成 1 / 4</span><strong>预计 19:35 完成</strong></div>
            <div className="progress-track"><span /></div>

            <div className="task-stack">
              {tasks.map((task, index) => (
                <article className="mini-task" key={task.subject}>
                  <span className={`task-dot ${task.tone}`}>{index + 1}</span>
                  <div><strong>{task.subject}</strong><p>{task.detail}</p></div>
                  <time>{task.time}</time>
                </article>
              ))}
            </div>

            <div className="didi-message">
              <span className="didi-orb">◕‿◕</span>
              <p><strong>嘀嘀已排好啦！</strong><br />先用朗读热身，再挑战数学，19:35 前轻松收尾。</p>
            </div>
          </section>
        </div>
      </section>
    </main>
  );
}
