import { defineConfig, type UserConfigExport } from '@tarojs/cli';
import path from 'node:path';
import devConfig from './dev';
import prodConfig from './prod';

export default defineConfig<'webpack5'>(async (merge) => {
  const baseConfig: UserConfigExport<'webpack5'> = {
    projectName: 'study-time-parent',
    date: '2026-09-09',
    designWidth: 375,
    deviceRatio: {
      375: 2,
      640: 1.17,
      750: 1,
    },
    sourceRoot: 'src',
    outputRoot: process.env.TARO_ENV === 'h5' ? 'dist-h5' : 'dist',
    framework: 'react',
    compiler: 'webpack5',
    compile: {
      include: [path.resolve(__dirname, '../../../packages/shared/src')],
    },
    cache: { enable: true },
    mini: {
      postcss: {
        pxtransform: { enable: true },
        url: { enable: true, config: { limit: 1024 } },
        cssModules: { enable: false },
      },
    },
    h5: {
      publicPath: '/',
      staticDirectory: 'static',
      devServer: {
        port: 5174,
        proxy: [
          {
            context: ['/api'],
            target: 'http://127.0.0.1:8080',
          },
        ],
      },
    },
  };

  if (process.env.NODE_ENV === 'development') {
    return merge({}, baseConfig, devConfig);
  }
  return merge({}, baseConfig, prodConfig);
});
