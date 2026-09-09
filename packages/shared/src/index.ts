export type ChildProfile = {
  id: string;
  name: string;
  grade: number;
  bedtime: string;
  stars: number;
};

export type DemoContext = {
  familyId: string;
  familyName: string;
  parentId: string;
  parentName: string;
  parentRelation: string;
  children: ChildProfile[];
};

export type RecognizedTask = {
  id: string;
  subject: string;
  title: string;
  taskType: string;
  icon: string;
  estimatedMinutes: number;
  estimateSource: 'GRADE_DEFAULT' | 'HISTORY';
  difficulty: 'EASY' | 'MODERATE' | 'CHALLENGE';
  eyeLoad: 'LOW' | 'HIGH';
  confidence: 'LOW' | 'HIGH';
  status: string;
};

export type HomeworkBatch = {
  id: string;
  childId: string;
  status: string;
  recognitionMode: string;
  sourceObjectKey: string | null;
  tasks: RecognizedTask[];
};

export type PlanItem = {
  id: string;
  homeworkTaskId: string | null;
  kind: 'HOMEWORK' | 'BREAK' | 'ROUTINE';
  subject: string;
  title: string;
  taskType: string;
  icon: string;
  estimatedMinutes: number;
  sortOrder: number;
  plannedStart: string;
  plannedEnd: string;
  status: 'PENDING' | 'ACTIVE' | 'DONE' | 'SKIPPED';
  actualSeconds: number;
  startedAt: string | null;
  overrunDecision: 'SKIP' | 'CONTINUE' | null;
};

export type TodayPlan = {
  id: string;
  childId: string;
  planDate: string;
  startTime: string;
  originalEndTime: string;
  plannedEndTime: string;
  bedtimeBufferMinutes: number;
  warningMessage: string | null;
  status: string;
  version: number;
  items: PlanItem[];
};

export type RealtimeEvent = {
  type:
    | 'HOMEWORK_RECOGNIZED'
    | 'PLAN_CREATED'
    | 'TASK_STARTED'
    | 'TASK_COMPLETED'
    | 'PLAN_ADJUSTED'
    | 'PLAN_RESCHEDULED'
    | 'PLAN_REORDERED'
    | 'WEEKLY_REPORT_UPDATED'
    | 'REWARD_UPDATED'
    | 'REWARD_REQUESTED'
    | 'REWARD_APPROVED'
    | 'REWARD_REJECTED';
  childId: string;
  occurredAt: string;
};

export type Activity = {
  id: string;
  actorName: string;
  actorRelation: string;
  actionType: string;
  description: string;
  createdAt: string;
};

export type SubjectSummary = {
  subject: string;
  completedTasks: number;
  averageEstimatedMinutes: number;
  averageActualMinutes: number;
};

export type Badge = {
  id: string;
  icon: string;
  title: string;
  description: string;
  unlocked: boolean;
};

export type WeeklyComment = {
  id: string;
  actorName: string;
  actorRelation: string;
  content: string;
  createdAt: string;
};

export type WeeklyReport = {
  weekStart: string;
  weekEnd: string;
  completedTasks: number;
  totalTasks: number;
  completionRate: number;
  focusedMinutes: number;
  starsEarned: number;
  streakDays: number;
  subjects: SubjectSummary[];
  badges: Badge[];
  comments: WeeklyComment[];
};

export type Reward = {
  id: string;
  name: string;
  icon: string;
  requiredStars: number;
  category: string;
  sourceType: 'BUILTIN' | 'CUSTOM';
  createdByName: string;
  canRedeem: boolean;
  redemptionId: string | null;
  redemptionStatus: 'REQUESTED' | 'APPROVED' | 'REJECTED' | null;
};

export type RewardStore = {
  childStars: number;
  rewards: Reward[];
};

export const demoChildId = 'demo-child-xiaoman';

export function gradeLabel(grade: number) {
  const labels = ['一', '二', '三', '四', '五', '六'];
  return `${labels[grade - 1] ?? grade}年级`;
}
