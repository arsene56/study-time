import { Check, Clock3, RefreshCw, Sparkles, Star } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  demoChildId,
  gradeLabel,
  type DemoContext,
  type PlanItem,
  type RealtimeEvent,
  type TodayPlan,
} from '@study-time/shared';

const apiBase = import.meta.env.VITE_API_BASE ?? '';
const selectedChildId = new URLSearchParams(window.location.search).get('childId') ?? demoChildId;

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

export default function App() {
  const [context, setContext] = useState<DemoContext | null>(null);
  const [plan, setPlan] = useState<TodayPlan | null>(null);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [message, setMessage] = useState('正在和嘀嘀连接…');

  const loadPlan = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const [contextData, planData] = await Promise.all([
        readJson<DemoContext>('/api/v1/demo/context'),
        readJson<TodayPlan>(`/api/v1/children/${selectedChildId}/today-plan`),
      ]);
      setContext(contextData);
      setPlan(planData);
      setMessage('计划已和家长端同步');
    } catch (error) {
      try {
        const contextData = await readJson<DemoContext>('/api/v1/demo/context');
        setContext(contextData);
      } catch {
        // Keep the actionable connection hint below.
      }
      setPlan(null);
      setMessage(error instanceof Error ? error.message : '暂时无法读取计划');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const initialRefresh = window.setTimeout(() => void loadPlan(), 0);
    return () => window.clearTimeout(initialRefresh);
  }, [loadPlan]);

  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = apiBase ? new URL(apiBase).host : window.location.host;
    const socket = new WebSocket(`${protocol}//${host}/ws/updates?childId=${selectedChildId}`);
    socket.onmessage = (event) => {
      const update = JSON.parse(event.data) as RealtimeEvent;
      if (update.childId === selectedChildId) void loadPlan(true);
    };
    return () => socket.close();
  }, [loadPlan]);

  const child = context?.children.find((item) => item.id === selectedChildId);
  const finished = plan?.items.filter((item) => item.status === 'DONE').length ?? 0;
  const total = plan?.items.length ?? 0;
  const progress = total === 0 ? 0 : Math.round((finished / total) * 100);
  const nextItem = useMemo(() => plan?.items.find((item) => item.status !== 'DONE') ?? null, [plan]);

  const complete = async (item: PlanItem) => {
    setBusyId(item.id);
    try {
      const updated = await readJson<TodayPlan>(`/api/v1/plan-items/${item.id}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          actorId: selectedChildId.replace('demo-child-', 'demo-child-member-'),
          actorName: child?.name ?? '林小满',
          actorRelation: '孩子',
        }),
      });
      setPlan(updated);
      setMessage(item.kind === 'BREAK' ? '休息完成，眼睛也充好电啦' : '完成一项！嘀嘀为你点亮了 5 颗星');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '打卡失败，请再试一次');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <main className="app-shell">
      <header className="hero-card">
        <div className="hero-copy">
          <p className="eyebrow">作业时光 · 孩子端</p>
          <h1>{child ? `${child.name}，今天也一起加油` : '嘀嘀正在准备今天的计划'}</h1>
          <p className="sync-line"><span className="live-dot" />{message}</p>
        </div>
        <div className="didi-wrap" aria-label="小时光机器人嘀嘀">
          <img src="/didi-mascot.png" alt="小时光机器人嘀嘀" />
          <span>嘀嘀</span>
        </div>
      </header>

      <section className="summary-grid" aria-label="今日计划概览">
        <article>
          <Clock3 />
          <span>预计完成</span>
          <strong>{plan?.plannedEndTime ?? '--:--'}</strong>
        </article>
        <article>
          <Star />
          <span>我的星星</span>
          <strong>{child?.stars ?? '--'}</strong>
        </article>
        <article>
          <Sparkles />
          <span>当前进度</span>
          <strong>{progress}%</strong>
        </article>
      </section>

      <section className="plan-section">
        <div className="section-heading">
          <div>
            <p>{child ? gradeLabel(child.grade) : '今日任务'}</p>
            <h2>我的放学后计划</h2>
          </div>
          <button className="icon-button" type="button" onClick={() => void loadPlan()} aria-label="刷新计划">
            <RefreshCw className={loading ? 'spin' : ''} />
          </button>
        </div>

        {!loading && !plan && (
          <div className="empty-card">
            <div className="empty-icon">📷</div>
            <h3>计划还在路上</h3>
            <p>请让家长在小程序中识别作业并确认计划，生成后这里会自动出现。</p>
          </div>
        )}

        {loading && <div className="loading-card">嘀嘀正在整理任务顺序…</div>}

        <div className="task-list">
          {plan?.items.map((item) => {
            const isNext = nextItem?.id === item.id;
            const done = item.status === 'DONE';
            return (
              <article className={`task-card ${taskTone(item)} ${done ? 'done' : ''}`} key={item.id}>
                <div className="task-time">
                  <strong>{item.plannedStart}</strong>
                  <span>{item.estimatedMinutes} 分钟</span>
                </div>
                <div className="task-icon" aria-hidden="true">{item.icon}</div>
                <div className="task-copy">
                  <div className="task-labels">
                    <span>{item.subject}</span>
                    {isNext && <em>接下来</em>}
                  </div>
                  <h3>{item.title}</h3>
                  <p>{item.taskType} · {item.plannedStart}–{item.plannedEnd}</p>
                </div>
                <button
                  type="button"
                  className={`complete-button ${done ? 'completed' : ''}`}
                  disabled={done || busyId !== null}
                  onClick={() => void complete(item)}
                  aria-label={done ? `${item.title}已完成` : `完成${item.title}`}
                >
                  <Check />
                  <span>{done ? '完成' : '打卡'}</span>
                </button>
              </article>
            );
          })}
        </div>
      </section>
    </main>
  );
}
