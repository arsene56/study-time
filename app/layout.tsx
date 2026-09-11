import type { Metadata } from 'next';
import './globals.css';
import { Providers } from './providers';

export const metadata: Metadata = {
  metadataBase: new URL('https://zuoye-shiguang-didi.arsene56.chatgpt.site'),
  title: '作业时光｜自主规划，快乐成长',
  description: '帮助小学生和家长识别、规划并自主完成家庭作业的双端体验原型。',
  applicationName: '作业时光',
  manifest: '/manifest.webmanifest',
  icons: { icon: '/favicon.svg' },
  openGraph: {
    title: '作业时光｜自主规划，快乐成长',
    description: '拍照识别作业，智能排期；家长安心看见，学生自主完成。',
    type: 'website',
    images: [{ url: '/og.png', width: 1200, height: 630, alt: '作业时光与小时光机器人嘀嘀' }],
  },
  twitter: {
    card: 'summary_large_image',
    title: '作业时光｜自主规划，快乐成长',
    description: '拍照识别作业，智能排期；家长安心看见，学生自主完成。',
    images: ['/og.png'],
  },
};

export const viewport = {
  width: 'device-width',
  initialScale: 1,
  themeColor: '#63c7a5',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body><Providers>{children}</Providers></body>
    </html>
  );
}
