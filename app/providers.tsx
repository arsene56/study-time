'use client';

import { PrototypeProvider } from '@/components/prototype-provider';

export function Providers({ children }: { children: React.ReactNode }) {
  return <PrototypeProvider>{children}</PrototypeProvider>;
}
