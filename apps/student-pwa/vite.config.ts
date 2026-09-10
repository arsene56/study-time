import type { IncomingMessage, ServerResponse } from 'node:http';
import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';

function studentQueryGuard(): Plugin {
  const guard = (request: IncomingMessage, response: ServerResponse, next: () => void) => {
    const url = new URL(request.url ?? '/', 'http://localhost');
    const unsupported = url.pathname === '/'
      && Array.from(url.searchParams.keys()).some((key) => key !== 'studentId');

    if (!unsupported) {
      next();
      return;
    }

    response.statusCode = 410;
    response.setHeader('Content-Type', 'text/plain; charset=utf-8');
    response.end('该链接已失效，请使用新的学生端链接。');
  };

  return {
    name: 'student-query-guard',
    configureServer(server) {
      server.middlewares.use(guard);
    },
    configurePreviewServer(server) {
      server.middlewares.use(guard);
    },
  };
}

export default defineConfig({
  plugins: [studentQueryGuard(), react()],
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:8080',
      '/ws': {
        target: 'ws://127.0.0.1:8080',
        ws: true,
      },
    },
  },
});
