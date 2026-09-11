'use client';

import { useState } from 'react';
import {
  ArrowLeft, ArrowRight, Check, Clock3, Eye, Plus, QrCode, ShieldCheck,
  Sparkles, UserRound, UsersRound,
} from 'lucide-react';
import { usePrototype } from '@/components/prototype-provider';

const steps = ['你的身份', '建立家庭', '添加学生', '设置作息', '健康用眼', '绑定设备'];

export default function OnboardingPage() {
  const { setFamilyReady } = usePrototype();
  const [step, setStep] = useState(0);
  const [relation, setRelation] = useState('妈妈');
  const [selectedStudent, setSelectedStudent] = useState('xiaoman');
  const [bound, setBound] = useState(false);

  function finish() {
    setFamilyReady(true);
    window.location.assign('/parent');
  }

  return (
    <main className="onboarding-page">
      <div className="onboarding-topbar">
        <a href="/" className="onboarding-brand"><Clock3 /><span><strong>作业时光</strong><small>自主规划，快乐成长</small></span></a>
        <span className="secure-note"><ShieldCheck />演示数据仅保存在本机</span>
      </div>

      <section className="onboarding-shell">
        <aside className="onboarding-aside">
          <span className="onboarding-kicker">欢迎来到作业时光</span>
          <h1>一起搭好学生的<br />自主规划空间</h1>
          <p>大约 2 分钟完成。嘀嘀会根据年级、作息与作业量，准备第一份个性化计划。</p>
          <div className="onboarding-step-list">
            {steps.map((label, index) => (
              <div key={label} className={`${index === step ? 'active' : ''} ${index < step ? 'done' : ''}`}>
                <span>{index < step ? <Check /> : index + 1}</span><p><strong>{label}</strong><small>{index === 0 ? '确定家庭关系' : index === 1 ? '创建共享空间' : index === 2 ? '年级与学习节奏' : index === 3 ? '固定模板和当天例外' : index === 4 ? '按年级设置默认规则' : '家长微信扫码完成'}</small></p>
              </div>
            ))}
          </div>
          <img src="/assets/didi-mascot.png" alt="小时光机器人嘀嘀" />
        </aside>

        <section className="onboarding-card">
          <div className="onboarding-progress"><span style={{ width: `${(step + 1) / steps.length * 100}%` }} /></div>
          <div className="step-counter">第 {step + 1} 步，共 {steps.length} 步</div>

          {step === 0 && (
            <div className="onboarding-panel identity-panel">
              <span className="panel-icon"><UserRound /></span><h2>你和学生是什么关系？</h2><p>以后每次调整计划，家人都能看到是谁做出的改变。</p>
              <div className="relation-grid">
                {['妈妈','爸爸','其他照护人'].map((item) => <button className={relation === item ? 'selected' : ''} onClick={() => setRelation(item)} key={item}><span>{item === '妈妈' ? '👩' : item === '爸爸' ? '👨' : '🤝'}</span><strong>{item}</strong>{relation === item && <Check />}</button>)}
              </div>
              <div className="wechat-account"><span>微</span><p><strong>微信账号已授权</strong><small>林小满妈妈 · 头像与昵称仅用于家庭内显示</small></p><Check /></div>
            </div>
          )}

          {step === 1 && (
            <div className="onboarding-panel family-panel">
              <span className="panel-icon"><UsersRound /></span><h2>给家庭空间起个名字</h2><p>家长与学生会在同一个家庭空间中协作，多名学生的数据相互隔离。</p>
              <label className="onboarding-field"><span>家庭名称</span><input defaultValue="林家的作业时光" /></label>
              <div className="family-preview"><span className="family-house">🏡</span><div><strong>林家的作业时光</strong><p>{relation}创建 · 1 位家庭成员</p></div><span>家庭空间</span></div>
              <button className="invite-later"><Plus />稍后还可以邀请爸爸或其他照护人</button>
            </div>
          )}

          {step === 2 && (
            <div className="onboarding-panel students-panel">
              <span className="panel-icon"><Sparkles /></span><h2>添加需要规划作业的学生</h2><p>我们准备了两名演示学生，用来体验多名学生切换和独立数据。</p>
              <div className="onboarding-student-list">
                <button className={selectedStudent === 'xiaoman' ? 'selected' : ''} onClick={() => setSelectedStudent('xiaoman')}><span className="student-demo-avatar mint">满</span><p><strong>林小满</strong><small>三年级 · 默认专注 25 分钟</small></p><b>{selectedStudent === 'xiaoman' ? <Check /> : '编辑'}</b></button>
                <button className={selectedStudent === 'keke' ? 'selected' : ''} onClick={() => setSelectedStudent('keke')}><span className="student-demo-avatar orange">可</span><p><strong>林可可</strong><small>一年级 · 默认专注 20 分钟</small></p><b>{selectedStudent === 'keke' ? <Check /> : '编辑'}</b></button>
              </div>
              <button className="add-another-student"><Plus />再添加一名学生</button>
              <div className="isolation-note"><ShieldCheck /><p><strong>多学生数据隔离</strong><small>每名学生有独立的任务、用时曲线、星星和奖励记录。</small></p></div>
            </div>
          )}

          {step === 3 && (
            <div className="onboarding-panel routine-panel">
              <span className="panel-icon"><Clock3 /></span><h2>设置每周固定作息</h2><p>先创建常用模板，遇到兴趣班、聚会等情况，当天可以临时调整。</p>
              <div className="routine-days">{['一','二','三','四','五','六','日'].map((day, index) => <button className={index < 5 ? 'selected' : ''} key={day}>周{day}</button>)}</div>
              <div className="routine-fields"><label><span>到家时间</span><input type="time" defaultValue="17:20" /></label><label><span>晚饭时间</span><input type="time" defaultValue="18:55" /></label><label><span>准备睡觉</span><input type="time" defaultValue="21:30" /></label></div>
              <div className="temporary-example"><span>🏀</span><p><strong>当天例外示例</strong><small>本周二 20:00–21:00 篮球兴趣班</small></p><span>只影响当天</span></div>
              <p className="buffer-explain">嘀嘀会先计算作业与必要休息，再尽量为阅读、洗漱和放松保留 30–60 分钟。</p>
            </div>
          )}

          {step === 4 && (
            <div className="onboarding-panel eye-panel">
              <span className="panel-icon"><Eye /></span><h2>按年级设置健康用眼节奏</h2><p>这是辅助养成习惯的默认值，家长和学生以后都可以一起调整。</p>
              <div className="grade-eye-grid"><article><span>1–2 年级</span><strong>20 分钟</strong><small>完整休息 10 分钟</small></article><article className="active"><span>3–4 年级</span><strong>25 分钟</strong><small>完整休息 10 分钟</small><b>小满</b></article><article><span>5–6 年级</span><strong>30 分钟</strong><small>完整休息 10 分钟</small></article></div>
              <div className="eye-principles"><p><Check />每 20 分钟提醒远眺约 6 米外 20 秒</p><p><Check />阅读、绘画等同样计入近距离用眼</p><p><Check />休息时推荐远眺、走动、喝水和伸展</p><p><Check />时间不足时也不会自动压缩必要休息</p></div>
              <div className="health-source"><ShieldCheck />依据权威指南辅助养成健康用眼习惯，不提供医疗诊断。</div>
            </div>
          )}

          {step === 5 && (
            <div className="onboarding-panel bind-panel">
              {!bound ? (
                <><span className="panel-icon"><QrCode /></span><h2>绑定学生的平板或学习机</h2><p>在学生设备打开作业时光，再用当前家长微信扫码完成绑定。</p><div className="large-mock-qr"><QrCode /></div><div className="bind-steps"><span><b>1</b>学生设备打开绑定页</span><i>→</i><span><b>2</b>家长微信扫码</span><i>→</i><span><b>3</b>选择学生账号</span></div><button className="simulate-bind" onClick={() => setBound(true)}>模拟扫码绑定林小满</button><small className="demo-qr-note">原型二维码不会连接真实微信</small></>
              ) : (
                <div className="bind-success"><div className="success-rings"><Check /></div><span>绑定成功</span><h2>林小满的平板已加入家庭</h2><p>家长录入作业后，计划会自动同步；双方的调整也会保留姓名与时间。</p><div className="bound-device"><span>▯</span><p><strong>小满的学习平板</strong><small>刚刚绑定 · 平板竖屏</small></p><b>在线</b></div><img src="/assets/didi-mascot.png" alt="嘀嘀庆祝绑定成功" /></div>
              )}
            </div>
          )}

          <footer className="onboarding-actions">
            <button className="back-step" disabled={step === 0} onClick={() => setStep(step - 1)}><ArrowLeft />上一步</button>
            {step < steps.length - 1 ? <button className="next-step" onClick={() => setStep(step + 1)}>下一步<ArrowRight /></button> : <button className="next-step" disabled={!bound} onClick={finish}>进入家长端<ArrowRight /></button>}
          </footer>
        </section>
      </section>
    </main>
  );
}
