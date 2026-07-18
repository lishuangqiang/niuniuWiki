// This file configures the initialization of Sentry on the server.
// The config you add here will be used whenever the server handles a request.
// https://docs.sentry.io/platforms/javascript/guides/nextjs/

import * as Sentry from '@sentry/nextjs';

const sentryDsn = process.env.SENTRY_DSN;

// 遥测默认关闭，仅在当前主体显式配置 DSN 后启用。
if (process.env.NODE_ENV === 'production' && sentryDsn) {
  Sentry.init({
    dsn: sentryDsn,

    // Enable logs to be sent to Sentry
    enableLogs: true,

    // Setting this option to true will print useful information to the console while you're setting up Sentry.
    debug: false,
  });
}
