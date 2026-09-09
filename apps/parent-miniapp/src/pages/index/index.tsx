import { Button, Image, Input, ScrollView, Text, View } from '@tarojs/components';
import Taro, { useDidShow } from '@tarojs/taro';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  gradeLabel,
  type DemoContext,
  type HomeworkBatch,
  type RealtimeEvent,
  type TodayPlan,
} from '@study-time/shared';
import didiMascot from '../../assets/didi-mascot.png';
import './index.css';

const apiBase = process.env.TARO_APP_API_BASE ?? (process.env.TARO_ENV === 'h5' ? '' : 'http://127.0.0.1:8080');

async function request<T>(options: Taro.request.Option): Promise<T> {
  const response = await Taro.request<T>({ ...options, url: `${apiBase}${options.url}` });
  if (response.statusCode < 200 || response.statusCode >= 300) {
    const data = response.data as { message?: string };
    throw new Error(data?.message ?? `请求失败（${response.statusCode}）`);
  }
  return response.data;
}

export default function ParentHome() {
  const [context, setContext] = useState<DemoContext | null>(null);
  const [childId, setChildId] = useState('demo-child-xiaoman');
  const [batch, setBatch] = useState<HomeworkBatch | null>(null);
  const [plan, setPlan] = useState<TodayPlan | null>(null);
  const [photoPath, setPhotoPath] = useState('');
  const [startTime, setStartTime] = useState('17:30');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('本地演示登录：林妈妈');

  const child = useMemo(() => context?.children.find((item) => item.id === childId), [childId, context]);

  const loadContext = useCallback(async () => {
    const data = await request<DemoContext>({ url: '/api/v1/demo/context', method: 'GET' });
    setContext(data);
  }, []);

  const loadPlan = useCallback(async (quiet = false) => {
    try {
      const data = await request<TodayPlan>({ url: `/api/v1/children/${childId}/today-plan`, method: 'GET' });
      setPlan(data);
      if (!quiet) setNotice('已读取孩子端同步计划');
    } catch {
      setPlan(null);
    }
  }, [childId]);

  useDidShow(() => {
    void loadContext();
    void loadPlan(true);
  });

  useEffect(() => {
    const initialRefresh = setTimeout(() => void loadPlan(true), 0);
    const wsBase = apiBase ? apiBase.replace(/^http/, 'ws') : 'ws://127.0.0.1:8080';
    let cancelled = false;
    let socket: Awaited<ReturnType<typeof Taro.connectSocket>> | undefined;
    const connect = async () => {
      socket = await Taro.connectSocket({ url: `${wsBase}/ws/updates?childId=${childId}` });
      if (cancelled) {
        socket.close({});
        return;
      }
      socket.onMessage((event) => {
        const update = JSON.parse(event.data as string) as RealtimeEvent;
        if (update.childId === childId) void loadPlan(true);
      });
    };
    void connect();
    return () => {
      cancelled = true;
      clearTimeout(initialRefresh);
      socket?.close({});
    };
  }, [childId, loadPlan]);

  const choosePhoto = async () => {
    try {
      const result = await Taro.chooseMedia({ count: 1, mediaType: ['image'], sourceType: ['album', 'camera'] });
      setPhotoPath(result.tempFiles[0]?.tempFilePath ?? '');
      setNotice('照片已选择，可开始模拟识别');
    } catch {
      setNotice('未选择照片，也可以直接使用预置示例');
    }
  };

  const recognize = async () => {
    setBusy(true);
    try {
      let recognized: HomeworkBatch;
      if (photoPath) {
        const response = await Taro.uploadFile({
          url: `${apiBase}/api/v1/homework-batches/mock-recognize?childId=${childId}`,
          filePath: photoPath,
          name: 'file',
        });
        if (response.statusCode < 200 || response.statusCode >= 300) throw new Error('照片上传失败');
        recognized = JSON.parse(response.data) as HomeworkBatch;
      } else {
        recognized = await request<HomeworkBatch>({
          url: `/api/v1/homework-batches/mock-recognize?childId=${childId}`,
          method: 'POST',
        });
      }
      setBatch(recognized);
      setNotice(`嘀嘀识别出 ${recognized.tasks.length} 项作业，请确认`);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : '识别失败，请重试');
    } finally {
      setBusy(false);
    }
  };

  const confirmPlan = async () => {
    if (!batch) return;
    setBusy(true);
    try {
      const created = await request<TodayPlan>({
        url: `/api/v1/homework-batches/${batch.id}/confirm-and-plan`,
        method: 'POST',
        header: { 'Content-Type': 'application/json' },
        data: { startTime },
      });
      setPlan(created);
      setBatch(null);
      setNotice(`计划已生成，预计 ${created.plannedEndTime} 完成`);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : '生成计划失败');
    } finally {
      setBusy(false);
    }
  };

  return (
    <ScrollView className="page" scrollY>
      <View className="hero">
        <View>
          <Text className="brand">作业时光 · 家长端</Text>
          <Text className="heroTitle">晚上好，林妈妈</Text>
          <Text className="subtitle">自主规划，快乐成长</Text>
        </View>
        <Image className="didi" src={didiMascot} mode="aspectFit" />
      </View>

      <View className="notice"><Text>{notice}</Text></View>

      <View className="childTabs">
        {context?.children.map((item) => (
          <Button
            key={item.id}
            className={`childTab ${item.id === childId ? 'active' : ''}`}
            onClick={() => setChildId(item.id)}
          >
            <Text className="avatar">{item.name.slice(-1)}</Text>
            <View>
              <Text className="childName">{item.name}</Text>
              <Text className="childGrade">{gradeLabel(item.grade)}</Text>
            </View>
          </Button>
        ))}
      </View>

      <View className="card uploadCard">
        <View className="cardHead">
          <View>
            <Text className="step">第一步</Text>
            <Text className="cardTitle">录入今天的作业</Text>
          </View>
          <Text className="statusPill">模拟 OCR</Text>
        </View>
        {photoPath ? <Image className="preview" src={photoPath} mode="aspectFill" /> : (
          <View className="photoPlaceholder">
            <Text className="photoIcon">📷</Text>
            <Text>黑板照片或家长群截图</Text>
          </View>
        )}
        <View className="buttonRow">
          <Button className="secondaryButton" onClick={() => void choosePhoto()}>选择照片</Button>
          <Button className="primaryButton" loading={busy} disabled={busy} onClick={() => void recognize()}>
            {photoPath ? '上传并识别' : '使用预置示例'}
          </Button>
        </View>
      </View>

      {batch && (
        <View className="card">
          <View className="cardHead">
            <View>
              <Text className="step">第二步</Text>
              <Text className="cardTitle">确认识别结果</Text>
            </View>
            <Text className="pendingPill">待确认</Text>
          </View>
          <View className="recognizedList">
            {batch.tasks.map((task) => (
              <View className="recognizedItem" key={task.id}>
                <Text className="taskEmoji">{task.icon}</Text>
                <View className="taskText">
                  <Text className="taskSubject">{task.subject} · {task.taskType}</Text>
                  <Text className="taskTitle">{task.title}</Text>
                </View>
                <Text className="minutes">{task.estimatedMinutes} 分钟</Text>
              </View>
            ))}
          </View>
          <View className="startRow">
            <Text>计划开始时间</Text>
            <Input className="timeInput" value={startTime} onInput={(event) => setStartTime(event.detail.value)} />
          </View>
          <Button className="confirmButton" loading={busy} disabled={busy} onClick={() => void confirmPlan()}>
            确认并生成智能计划
          </Button>
        </View>
      )}

      {plan && (
        <View className="card planCard">
          <View className="cardHead">
            <View>
              <Text className="step">今日计划</Text>
              <Text className="cardTitle">{child?.name}预计 {plan.plannedEndTime} 完成</Text>
            </View>
            <Text className="readyPill">已同步</Text>
          </View>
          {plan.items.map((item) => (
            <View className={`planItem ${item.status === 'DONE' ? 'done' : ''}`} key={item.id}>
              <Text className="planTime">{item.plannedStart}</Text>
              <Text className="taskEmoji">{item.icon}</Text>
              <View className="taskText">
                <Text className="taskTitle">{item.title}</Text>
                <Text className="taskSubject">{item.subject} · {item.estimatedMinutes} 分钟</Text>
              </View>
              <Text className="itemStatus">{item.status === 'DONE' ? '已完成' : '待完成'}</Text>
            </View>
          ))}
        </View>
      )}
    </ScrollView>
  );
}
