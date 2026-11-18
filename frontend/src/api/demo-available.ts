// Entry point for the in-browser demo API. Imported as '@demo'; production builds alias it to
// demo-unavailable.ts so the mock and its sample data are not shipped (see vite.config.ts).
export { DemoApi, DEMO_USERS } from './demo';
