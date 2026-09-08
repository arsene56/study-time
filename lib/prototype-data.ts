export type TaskStatus = 'pending' | 'active' | 'paused' | 'done' | 'skipped';
export type TaskKind = 'homework' | 'break' | 'routine';

export type HomeworkTask = {
  id: string;
  subject: string;
  title: string;
  type: string;
  icon: string;
  estimatedMinutes: number;
  actualSeconds: number;
  start: string;
  end: string;
  eyeLoad: 'high' | 'low';
  difficulty: '轻松' | '适中' | '挑战';
  status: TaskStatus;
  kind: TaskKind;
  confidence?: 'high' | 'low';
  needsHelp?: boolean;
  evidence?: boolean;
};

export type ChildProfile = {
  id: string;
  name: string;
  grade: string;
  avatar: string;
  theme: 'mint' | 'orange';
  streak: number;
  stars: number;
  claimed: boolean;
  plannedEnd: string;
  originalEnd: string;
  bedtime: string;
  tasks: HomeworkTask[];
};

export type ActivityEntry = {
  id: string;
  childId: string;
  actor: string;
  relation: string;
  action: string;
  time: string;
  tone: 'mint' | 'orange' | 'blue';
};

export type RewardItem = {
  id: string;
  title: string;
  icon: string;
  stars: number;
  status: 'available' | 'pending' | 'locked';
  custom?: boolean;
};

export function getFocusMinutes(grade: string) {
  if (grade.includes('一') || grade.includes('二')) return 20;
  if (grade.includes('三') || grade.includes('四')) return 25;
  return 30;
}

export const initialChildren: ChildProfile[] = [
  {
    id: 'xiaoman',
    name: '林小满',
    grade: '三年级',
    avatar: '满',
    theme: 'mint',
    streak: 7,
    stars: 86,
    claimed: false,
    originalEnd: '19:35',
    plannedEnd: '19:35',
    bedtime: '21:30',
    tasks: [
      {
        id: 'xm-cn-read', subject: '语文', title: '朗读《荷花》两遍', type: '朗读', icon: '📖',
        estimatedMinutes: 12, actualSeconds: 0, start: '17:30', end: '17:42', eyeLoad: 'high',
        difficulty: '轻松', status: 'pending', kind: 'homework', confidence: 'high',
      },
      {
        id: 'xm-math', subject: '数学', title: '练习册第 32–33 页', type: '书写', icon: '✏️',
        estimatedMinutes: 30, actualSeconds: 0, start: '17:42', end: '18:12', eyeLoad: 'high',
        difficulty: '挑战', status: 'pending', kind: 'homework', confidence: 'high',
      },
      {
        id: 'xm-break-1', subject: '休息', title: '远眺＋走动一下', type: '护眼休息', icon: '🌿',
        estimatedMinutes: 10, actualSeconds: 0, start: '18:12', end: '18:22', eyeLoad: 'low',
        difficulty: '轻松', status: 'pending', kind: 'break',
      },
      {
        id: 'xm-cn-write', subject: '语文', title: '生字本第 12 页', type: '书写', icon: '📝',
        estimatedMinutes: 22, actualSeconds: 0, start: '18:22', end: '18:44', eyeLoad: 'high',
        difficulty: '适中', status: 'pending', kind: 'homework', confidence: 'high',
      },
      {
        id: 'xm-english', subject: '英语', title: 'Unit 2 单词跟读 3 遍', type: '口语', icon: '🎧',
        estimatedMinutes: 15, actualSeconds: 0, start: '18:44', end: '18:59', eyeLoad: 'low',
        difficulty: '轻松', status: 'pending', kind: 'homework', confidence: 'high',
      },
      {
        id: 'xm-science', subject: '科学', title: '观察一株植物并记录', type: '实践', icon: '🔍',
        estimatedMinutes: 20, actualSeconds: 0, start: '19:05', end: '19:25', eyeLoad: 'low',
        difficulty: '适中', status: 'pending', kind: 'homework', confidence: 'low',
      },
      {
        id: 'xm-pack', subject: '整理', title: '检查作业并整理书包', type: '整理', icon: '🎒',
        estimatedMinutes: 10, actualSeconds: 0, start: '19:25', end: '19:35', eyeLoad: 'low',
        difficulty: '轻松', status: 'pending', kind: 'routine',
      },
    ],
  },
  {
    id: 'keke',
    name: '林可可',
    grade: '一年级',
    avatar: '可',
    theme: 'orange',
    streak: 4,
    stars: 42,
    claimed: true,
    originalEnd: '18:45',
    plannedEnd: '18:45',
    bedtime: '20:45',
    tasks: [
      {
        id: 'kk-cn', subject: '语文', title: '拼音卡片朗读 2 遍', type: '朗读', icon: '🔤',
        estimatedMinutes: 8, actualSeconds: 480, start: '17:15', end: '17:23', eyeLoad: 'low',
        difficulty: '轻松', status: 'done', kind: 'homework', confidence: 'high', evidence: true,
      },
      {
        id: 'kk-math', subject: '数学', title: '口算练习 15 题', type: '口算', icon: '🧮',
        estimatedMinutes: 15, actualSeconds: 0, start: '17:23', end: '17:38', eyeLoad: 'high',
        difficulty: '适中', status: 'pending', kind: 'homework', confidence: 'high',
      },
      {
        id: 'kk-break', subject: '休息', title: '看看窗外，喝口水', type: '护眼休息', icon: '☁️',
        estimatedMinutes: 10, actualSeconds: 0, start: '17:38', end: '17:48', eyeLoad: 'low',
        difficulty: '轻松', status: 'pending', kind: 'break',
      },
      {
        id: 'kk-labor', subject: '劳动', title: '整理自己的书桌', type: '劳动', icon: '🧹',
        estimatedMinutes: 12, actualSeconds: 0, start: '17:48', end: '18:00', eyeLoad: 'low',
        difficulty: '轻松', status: 'pending', kind: 'homework', confidence: 'high',
      },
      {
        id: 'kk-art', subject: '美术', title: '完成“秋天的树叶”涂色', type: '绘画', icon: '🎨',
        estimatedMinutes: 25, actualSeconds: 0, start: '18:10', end: '18:35', eyeLoad: 'high',
        difficulty: '适中', status: 'pending', kind: 'homework', confidence: 'low',
      },
    ],
  },
];

export const initialActivities: ActivityEntry[] = [
  { id: 'a1', childId: 'xiaoman', actor: '妈妈', relation: '妈妈', action: '上传了今天的作业照片，嘀嘀已生成计划', time: '16:48', tone: 'mint' },
  { id: 'a2', childId: 'xiaoman', actor: '林小满', relation: '孩子', action: '把英语跟读移到了生字练习之后', time: '17:02', tone: 'orange' },
  { id: 'a3', childId: 'xiaoman', actor: '爸爸', relation: '爸爸', action: '将今晚兴趣班调整为 20:00 开始', time: '17:06', tone: 'blue' },
  { id: 'a4', childId: 'keke', actor: '林可可', relation: '孩子', action: '完成了拼音卡片朗读，获得 5 颗星', time: '17:23', tone: 'orange' },
];

export const initialRewards: RewardItem[] = [
  { id: 'r1', title: '嘀嘀·森林探险皮肤', icon: '🌲', stars: 60, status: 'available' },
  { id: 'r2', title: '周末亲子桌游 30 分钟', icon: '🎲', stars: 80, status: 'available', custom: true },
  { id: 'r3', title: '睡前故事由我来选', icon: '🌙', stars: 40, status: 'available', custom: true },
  { id: 'r4', title: '嘀嘀·星河皮肤', icon: '🌌', stars: 120, status: 'locked' },
];

export const weekChart = [
  { day: '一', estimated: 82, actual: 88 },
  { day: '二', estimated: 75, actual: 72 },
  { day: '三', estimated: 96, actual: 101 },
  { day: '四', estimated: 68, actual: 65 },
  { day: '五', estimated: 99, actual: 104 },
  { day: '六', estimated: 42, actual: 38 },
  { day: '日', estimated: 28, actual: 30 },
];
