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
};

export type TodayPlan = {
  id: string;
  childId: string;
  planDate: string;
  startTime: string;
  originalEndTime: string;
  plannedEndTime: string;
  status: string;
  version: number;
  items: PlanItem[];
};

export type RealtimeEvent = {
  type: 'HOMEWORK_RECOGNIZED' | 'PLAN_CREATED' | 'TASK_COMPLETED';
  childId: string;
  occurredAt: string;
};

export const demoChildId = 'demo-child-xiaoman';

export function gradeLabel(grade: number) {
  const labels = ['一', '二', '三', '四', '五', '六'];
  return `${labels[grade - 1] ?? grade}年级`;
}
