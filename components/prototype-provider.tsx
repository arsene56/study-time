'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import {
  initialActivities,
  initialStudents,
  initialRewards,
  type ActivityEntry,
  type StudentProfile,
  type RewardItem,
} from '@/lib/prototype-data';

type DemoState = {
  activeStudentId: string;
  students: StudentProfile[];
  activities: ActivityEntry[];
  rewards: RewardItem[];
  familyReady: boolean;
};

type DemoContextValue = DemoState & {
  activeStudent: StudentProfile;
  setActiveStudent: (id: string) => void;
  setFamilyReady: (ready: boolean) => void;
  claimPlan: () => void;
  moveTask: (taskId: string, direction: -1 | 1, actor?: 'student' | 'parent') => void;
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
  activeStudentId: 'xiaoman',
  students: initialStudents,
  activities: initialActivities,
  rewards: initialRewards,
  familyReady: false,
};

const DemoContext = createContext<DemoContextValue | null>(null);

function isDemoState(value: unknown): value is DemoState {
  if (!value || typeof value !== 'object') return false;
  const candidate = value as Partial<DemoState>;
  return typeof candidate.activeStudentId === 'string'
    && Array.isArray(candidate.students)
    && candidate.students.length > 0
    && Array.isArray(candidate.activities)
    && Array.isArray(candidate.rewards)
    && typeof candidate.familyReady === 'boolean';
}

function currentTime() {
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date());
}

function addMinutes(time: string, minutes: number) {
  const [hour, minute] = time.split(':').map(Number);
  const total = hour * 60 + minute + minutes;
  return `${String(Math.floor(total / 60) % 24).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}

function minutesSinceMidnight(time: string) {
  const [hour, minute] = time.split(':').map(Number);
  return hour * 60 + minute;
}

function effectiveMinutes(task: StudentProfile['tasks'][number]) {
  if (task.status === 'skipped') return 0;
  if (task.status === 'done' && task.actualSeconds > 0) {
    return Math.max(1, Math.ceil(task.actualSeconds / 60));
  }
  return task.estimatedMinutes;
}

function scheduleGaps(tasks: StudentProfile['tasks'], plannedEnd: string) {
  return tasks.map((task, index) => {
    const nextStart = tasks[index + 1]?.start ?? plannedEnd;
    return Math.max(0, minutesSinceMidnight(nextStart) - minutesSinceMidnight(task.end));
  });
}

function earliestStart(tasks: StudentProfile['tasks']) {
  return tasks.reduce((earliest, task) => (
    minutesSinceMidnight(task.start) < minutesSinceMidnight(earliest) ? task.start : earliest
  ), tasks[0]?.start ?? '17:30');
}

function rescheduleTasks(
  tasks: StudentProfile['tasks'],
  planStart: string,
  gapsAfter: number[],
) {
  let cursor = planStart;
  const scheduled = tasks.map((task, index) => {
    const start = cursor;
    const end = addMinutes(start, effectiveMinutes(task));
    cursor = addMinutes(end, gapsAfter[index] ?? 0);
    return { ...task, start, end };
  });
  return { tasks: scheduled, plannedEnd: cursor };
}

export function PrototypeProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<DemoState>(initialState);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    try {
      const saved = window.localStorage.getItem(storageKey);
      // Persisted demo data is intentionally hydrated after mount so the
      // server and first client render remain identical.
      if (saved) {
        const parsed: unknown = JSON.parse(saved);
        // oxlint-disable-next-line react/react-compiler
        if (isDemoState(parsed)) setState(parsed);
      }
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

  const activeStudent = state.students.find((student) => student.id === state.activeStudentId) ?? state.students[0];

  const addActivity = useCallback((entry: Omit<ActivityEntry, 'id' | 'time'>) => {
    setState((previous) => ({
      ...previous,
      activities: [{ ...entry, id: `a-${Date.now()}`, time: currentTime() }, ...previous.activities],
    }));
  }, []);

  const updateActiveStudent = useCallback((updater: (student: StudentProfile) => StudentProfile) => {
    setState((previous) => ({
      ...previous,
      students: previous.students.map((student) => student.id === previous.activeStudentId ? updater(student) : student),
    }));
  }, []);

  const value = useMemo<DemoContextValue>(() => ({
    ...state,
    activeStudent,
    setActiveStudent: (id) => setState((previous) => ({ ...previous, activeStudentId: id })),
    setFamilyReady: (ready) => setState((previous) => ({ ...previous, familyReady: ready })),
    claimPlan: () => {
      updateActiveStudent((student) => ({ ...student, claimed: true }));
      addActivity({ studentId: state.activeStudentId, actor: activeStudent.name, relation: '学生', action: '认领了今天的计划，准备开始', tone: 'orange' });
    },
    moveTask: (taskId, direction, actor = 'student') => {
      updateActiveStudent((student) => {
        const tasks = [...student.tasks];
        const index = tasks.findIndex((task) => task.id === taskId);
        const target = index + direction;
        if (index < 0 || target < 0 || target >= tasks.length) return student;
        const planStart = earliestStart(student.tasks);
        const gapsAfter = scheduleGaps(student.tasks, student.plannedEnd);
        [tasks[index], tasks[target]] = [tasks[target], tasks[index]];
        return { ...student, ...rescheduleTasks(tasks, planStart, gapsAfter) };
      });
      addActivity(actor === 'parent'
        ? { studentId: state.activeStudentId, actor: '妈妈', relation: '妈妈', action: '调整了两项任务的顺序，后续时间已同步更新', tone: 'mint' }
        : { studentId: state.activeStudentId, actor: activeStudent.name, relation: '学生', action: '自主调整了两项任务的顺序，后续时间已同步更新', tone: 'orange' });
    },
    startTask: (taskId) => {
      updateActiveStudent((student) => ({
        ...student,
        tasks: student.tasks.map((task) => ({
          ...task,
          status: task.id === taskId ? 'active' : task.status === 'active' ? 'paused' : task.status,
        })),
      }));
    },
    pauseTask: (taskId) => updateActiveStudent((student) => ({
      ...student,
      tasks: student.tasks.map((task) => task.id === taskId ? { ...task, status: 'paused' } : task),
    })),
    completeTask: (taskId, evidence = false) => {
      const task = activeStudent.tasks.find((item) => item.id === taskId);
      updateActiveStudent((student) => ({
        ...student,
        stars: student.stars + (task?.kind === 'break' ? 1 : 5),
        tasks: student.tasks.map((item) => item.id === taskId
          ? { ...item, status: 'done', evidence: evidence || item.evidence, actualSeconds: item.actualSeconds || item.estimatedMinutes * 60 }
          : item),
      }));
      if (task) addActivity({ studentId: state.activeStudentId, actor: activeStudent.name, relation: '学生', action: `完成了“${task.title}”，获得 ${task.kind === 'break' ? 1 : 5} 颗星`, tone: 'orange' });
    },
    tickTask: (taskId) => updateActiveStudent((student) => ({
      ...student,
      tasks: student.tasks.map((task) => task.id === taskId ? { ...task, actualSeconds: task.actualSeconds + 1 } : task),
    })),
    handleOverrun: (taskId, decision) => {
      updateActiveStudent((student) => {
        const selected = student.tasks.find((task) => task.id === taskId);
        let tasks = student.tasks.map((task) => task.id === taskId
          ? { ...task, status: decision === 'skip' ? 'skipped' as const : 'active' as const, needsHelp: decision === 'skip' }
          : task);
        if (decision === 'skip' && selected) {
          tasks = [...tasks.filter((task) => task.id !== taskId), tasks.find((task) => task.id === taskId)!];
        }
        return { ...student, tasks, plannedEnd: addMinutes(student.plannedEnd, 30) };
      });
      addActivity({
        studentId: state.activeStudentId,
        actor: activeStudent.name,
        relation: '学生',
        action: decision === 'skip' ? '暂时跳过了一项困难作业，已向家长求助' : '选择继续挑战超时作业，剩余计划已自动更新',
        tone: decision === 'skip' ? 'blue' : 'orange',
      });
    },
    addRewardRequest: (title, stars) => {
      setState((previous) => ({
        ...previous,
        rewards: [{ id: `r-${Date.now()}`, title, stars, icon: '✨', custom: true, status: 'pending' }, ...previous.rewards],
      }));
      addActivity({ studentId: state.activeStudentId, actor: activeStudent.name, relation: '学生', action: `发起了新奖励“${title}”的审批`, tone: 'orange' });
    },
    approveReward: (rewardId) => setState((previous) => ({
      ...previous,
      rewards: previous.rewards.map((reward) => reward.id === rewardId ? { ...reward, status: 'available' } : reward),
    })),
    redeemReward: (rewardId) => {
      const reward = state.rewards.find((item) => item.id === rewardId);
      if (!reward || reward.status !== 'available' || activeStudent.stars < reward.stars) return false;
      updateActiveStudent((student) => ({ ...student, stars: student.stars - reward.stars }));
      addActivity({ studentId: state.activeStudentId, actor: activeStudent.name, relation: '学生', action: `兑换了“${reward.title}”`, tone: 'orange' });
      return true;
    },
    resetDemo: () => {
      window.localStorage.removeItem(storageKey);
      setState(initialState);
    },
  }), [activeStudent, addActivity, state, updateActiveStudent]);

  return <DemoContext.Provider value={value}>{children}</DemoContext.Provider>;
}

export function usePrototype() {
  const context = useContext(DemoContext);
  if (!context) throw new Error('usePrototype must be used inside PrototypeProvider');
  return context;
}
