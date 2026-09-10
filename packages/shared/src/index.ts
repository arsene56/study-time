export type StudentProfile = {
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
  students: StudentProfile[];
};

export type RecognizedTask = {
  id: string;
  subject: string;
  title: string;
  taskType: string;
  icon: string;
  estimatedMinutes: number;
  baseEstimatedMinutes: number;
  estimateSource: 'GRADE_DEFAULT' | 'PERSONAL_HISTORY' | 'SUBJECT_HISTORY' | 'MANUAL_OVERRIDE';
  estimateSampleSize: number;
  estimateConfidence: 'LOW' | 'MEDIUM' | 'HIGH';
  estimateReason: string;
  difficulty: 'EASY' | 'MODERATE' | 'CHALLENGE';
  eyeLoad: 'LOW' | 'HIGH';
  confidence: 'LOW' | 'MEDIUM' | 'HIGH';
  ocrConfidence: number | null;
  manuallyEdited: boolean;
  status: string;
};

export type HomeworkBatch = {
  id: string;
  studentId: string;
  status: string;
  recognitionMode: string;
  sourceObjectKey: string | null;
  ocrProvider: string | null;
  ocrRequestId: string | null;
  ocrRawText: string | null;
  ocrAverageConfidence: number | null;
  recognitionError: string | null;
  recognizedAt: string | null;
  tasks: RecognizedTask[];
};

export type RecognitionCapability = {
  configuredProvider: string;
  providerLabel: string;
  realOcrAvailable: boolean;
  message: string;
};

export type SubjectPersonalization = {
  subject: string;
  sampleSize: number;
  averageEstimatedMinutes: number;
  averageActualMinutes: number;
  pacePercent: number;
  trend: string;
  suggestion: string;
};

export type PersonalizationProfile = {
  studentId: string;
  totalSamples: number;
  level: 'STARTING' | 'LEARNING' | 'STABLE';
  confidence: 'LOW' | 'MEDIUM' | 'HIGH';
  overallPacePercent: number;
  summary: string;
  subjects: SubjectPersonalization[];
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
  studentId: string;
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
    | 'REWARD_REJECTED'
    | 'WEEKLY_GOAL_UPDATED'
    | 'WEEKLY_BONUS_CLAIMED'
    | 'DIDI_SKIN_EQUIPPED';
  studentId: string;
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

export type Notification = {
  id: string;
  studentId: string;
  studentName: string;
  eventType: string;
  title: string;
  message: string;
  actionPath: string | null;
  read: boolean;
  createdAt: string;
};

export type NotificationFeed = {
  unreadCount: number;
  items: Notification[];
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
  progress: number;
  target: number;
  unlocked: boolean;
  unlockedAt: string | null;
};

export type DailyProgress = {
  date: string;
  dayLabel: string;
  completedTasks: number;
  totalTasks: number;
  completionRate: number;
  focusedMinutes: number;
};

export type WeeklyComparison = {
  completionRateChange: number;
  focusedMinutesChange: number;
  trendText: string;
};

export type WeeklyGoal = {
  id: string;
  targetTasks: number;
  targetFocusMinutes: number;
  bonusStars: number;
  taskProgress: number;
  focusProgress: number;
  overallProgress: number;
  achieved: boolean;
  status: 'IN_PROGRESS' | 'READY' | 'CLAIMED';
  claimedAt: string | null;
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
  growthMessage: string;
  dailyProgress: DailyProgress[];
  comparison: WeeklyComparison;
  goal: WeeklyGoal | null;
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
  owned: boolean;
  equipped: boolean;
  redemptionId: string | null;
  redemptionStatus: 'REQUESTED' | 'APPROVED' | 'REJECTED' | null;
};

export type RewardRedemption = {
  id: string;
  rewardId: string;
  rewardName: string;
  rewardIcon: string;
  requiredStars: number;
  status: 'REQUESTED' | 'APPROVED' | 'REJECTED';
  requestedByName: string;
  reviewedByName: string | null;
  requestedAt: string;
  reviewedAt: string | null;
};

export type StarTransaction = {
  id: string;
  amount: number;
  reason: string;
  createdAt: string;
};

export type RewardStore = {
  studentStars: number;
  equippedSkinRewardId: string | null;
  rewards: Reward[];
  redemptions: RewardRedemption[];
  starTransactions: StarTransaction[];
};

export const demoStudentId = 'demo-student-xiaoman';

export function gradeLabel(grade: number) {
  const labels = ['一', '二', '三', '四', '五', '六'];
  return `${labels[grade - 1] ?? grade}年级`;
}
