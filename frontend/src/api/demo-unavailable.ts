// Stand-in for '@demo' in builds without the demo sandbox (vite.config.ts). Nothing from demo.ts is bundled.
export const DEMO_USERS: { username: string; role: string; blurb: string }[] = [];

export class DemoApi {
  constructor() {
    throw new Error('The demo sandbox is not included in this build');
  }
  static reset() {}
  setUser(_username: string) {}
}
