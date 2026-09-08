'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import {
  initialActivities,
  initialChildren,
  initialRewards,
  type ActivityEntry,
  type ChildProfile,
  type RewardItem,
} from '@/lib/prototype-data';

type DemoState = {
  activeChildId: string;
  children: ChildProfile[];
  activities: ActivityEntry[];
  rewards: RewardItem[];
  familyReady: boolean;
};

type DemoContextValue = DemoState & {
  activeChild: ChildProfile;
  setActiveChild: (id: string) => void;
  setFamilyReady: (ready: boolean) => void;
  claimPlan: () => void;
  moveTask: (taskId: string, direction: -1 | 1, actor?: 'child' | 'parent') => void;
  startTask: (taskId: string) => void;
  pauseTask: (taskId: string) => void;
  completeTask: (taskId: string, evidence?: boolean) => void;
  tickTask: (taskId: string) => void;
  handleOverrun: (taskId: string, decision: 'skip' | 'challenge') => void;
  addRewardRequest: (title: string, stars: number) => void;
  approveReward: (rewardId: string) => void;
  redeemReward: (rewardId: string) => boolean;
  resetDemo: () => void;
};

const storageKey = 'homework-time-prototype-v1';

const initialState: DemoState = {
  activeChildId: 'xiaoman',
  children: initialChildren,
  activities: initialActivities,
  rewards: initialRewards,
  familyReady: false,
};

const DemoContext = createContext<DemoContextValue | null>(null);

function currentTime() {
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date());
}

function addMinutes(time: string, minutes: number) {
  const [hour, minute] = time.split(':').map(Number);
  const total = hour * 60 + minute + minutes;
  return `${String(Math.floor(total / 60) % 24).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}

export function PrototypeProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<DemoState>(initialState);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    try {
      const saved = window.localStorage.getItem(storageKey);
      // Persisted demo data is intentionally hydrated after mount so the
      // server and first client render remain identical.
      // oxlint-disable-next-line react/react-compiler
      if (saved) setState(JSON.parse(saved) as DemoState);
    } catch {
      // The prototype still works when storage is blocked.
    }
    // oxlint-disable-next-line react/react-compiler
    setHydrated(true);
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    window.localStorage.setItem(storageKey, JSON.stringify(state));
  }, [hydrated, state]);

  const activeChild = state.children.find((child) => child.id === state.activeChildId) ?? state.children[0];

  const addActivity = useCallback((entry: Omit<ActivityEntry, 'id' | 'time'>) => {
    setState((previous) => ({
      ...previous,
      activities: [{ ...entry, id: `a-${Date.now()}`, time: currentTime() }, ...previous.activities],
    }));
  }, []);

  const updateActiveChild = useCallback((updater: (child: ChildProfile) => ChildProfile) => {
    setState((previous) => ({
      ...previous,
      children: previous.children.map((child) => child.id === previous.activeChildId ? updater(child) : child),
    }));
  }, []);

  const value = useMemo<DemoContextValue>(() => ({
    ...state,
    activeChild,
    setActiveChild: (id) => setState((previous) => ({ ...previous, activeChildId: id })),
    setFamilyReady: (ready) => setState((previous) => ({ ...previous, familyReady: ready })),
    claimPlan: () => {
      updateActiveChild((child) => ({ ...child, claimed: true }));
      addActivity({ childId: state.activeChildId, actor: activeChild.name, relation: '孩子', action: '认领了今天的计划，准备开始', tone: 'orange' });
    },
    moveTask: (taskId, direction, actor = 'child') => {
      updateActiveChild((child) => {
        const tasks = [...child.tasks];
        const index = tasks.findIndex((task) => task.id === taskId);
        const target = index + direction;
        if (index < 0 || target < 0 || target >= tasks.length) return child;
        [tasks[index], tasks[target]] = [tasks[target], tasks[index]];
        return { ...child, tasks };
      });
      addActivity(actor === 'parent'
        ? { childId: state.activeChildId, actor: '妈妈', relation: '妈妈', action: '调整了两项任务的顺序', tone: 'mint' }
        : { childId: state.activeChildId, actor: activeChild.name, relation: '孩子', action: '自主调整了两项任务的顺序', tone: 'orange' });
    },
    startTask: (taskId) => {
      updateActiveChild((child) => ({
        ...child,
        tasks: child.tasks.map((task) => ({
          ...task,
          status: task.id === taskId ? 'active' : task.status === 'active' ? 'paused' : task.status,
        })),
      }));
    },
    pauseTask: (taskId) => updateActiveChild((child) => ({
      ...child,
      tasks: child.tasks.map((task) => task.id === taskId ? { ...task, status: 'paused' } : task),
    })),
    completeTask: (taskId, evidence = false) => {
      const task = activeChild.tasks.find((item) => item.id === taskId);
      updateActiveChild((child) => ({
        ...child,
        stars: child.stars + (task?.kind === 'break' ? 1 : 5),
        tasks: child.tasks.map((item) => item.id === taskId
          ? { ...item, status: 'done', evidence: evidence || item.evidence, actualSeconds: item.actualSeconds || item.estimatedMinutes * 60 }
          : item),
      }));
      if (task) addActivity({ childId: state.activeChildId, actor: activeChild.name, relation: '孩子', action: `完成了“${task.title}”，获得 ${task.kind === 'break' ? 1 : 5} 颗星`, tone: 'orange' });
    },
    tickTask: (taskId) => updateActiveChild((child) => ({
      ...child,
      tasks: child.tasks.map((task) => task.id === taskId ? { ...task, actualSeconds: task.actualSeconds + 1 } : task),
    })),
    handleOverrun: (taskId, decision) => {
      updateActiveChild((child) => {
        const selected = child.tasks.find((task) => task.id === taskId);
        let tasks = child.tasks.map((task) => task.id === taskId
          ? { ...task, status: decision === 'skip' ? 'skipped' as const : 'active' as const, needsHelp: decision === 'skip' }
          : task);
        if (decision === 'skip' && selected) {
          tasks = [...tasks.filter((task) => task.id !== taskId), tasks.find((task) => task.id === taskId)!];
        }
        return { ...child, tasks, plannedEnd: addMinutes(child.plannedEnd, 30) };
      });
      addActivity({
        childId: state.activeChildId,
        actor: activeChild.name,
        relation: '孩子',
        action: decision === 'skip' ? '暂时跳过了一项困难作业，已向家长求助' : '选择继续挑战超时作业，剩余计划已自动更新',
        tone: decision === 'skip' ? 'blue' : 'orange',
      });
    },
    addRewardRequest: (title, stars) => {
      setState((previous) => ({
        ...previous,
        rewards: [{ id: `r-${Date.now()}`, title, stars, icon: '✨', custom: true, status: 'pending' }, ...previous.rewards],
      }));
      addActivity({ childId: state.activeChildId, actor: activeChild.name, relation: '孩子', action: `发起了新奖励“${title}”的审批`, tone: 'orange' });
    },
    approveReward: (rewardId) => setState((previous) => ({
      ...previous,
      rewards: previous.rewards.map((reward) => reward.id === rewardId ? { ...reward, status: 'available' } : reward),
    })),
    redeemReward: (rewardId) => {
      const reward = state.rewards.find((item) => item.id === rewardId);
      if (!reward || reward.status !== 'available' || activeChild.stars < reward.stars) return false;
      updateActiveChild((child) => ({ ...child, stars: child.stars - reward.stars }));
      addActivity({ childId: state.activeChildId, actor: activeChild.name, relation: '孩子', action: `兑换了“${reward.title}”`, tone: 'orange' });
      return true;
    },
    resetDemo: () => {
      window.localStorage.removeItem(storageKey);
      setState(initialState);
    },
  }), [activeChild, addActivity, state, updateActiveChild]);

  return <DemoContext.Provider value={value}>{children}</DemoContext.Provider>;
}

export function usePrototype() {
  const context = useContext(DemoContext);
  if (!context) throw new Error('usePrototype must be used inside PrototypeProvider');
  return context;
}
