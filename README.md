# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Babel](https://babeljs.io/) for Fast Refresh
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/) for Fast Refresh

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type-aware lint rules:

```js
export default tseslint.config([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...

      // Remove tseslint.configs.recommended and replace with this
      ...tseslint.configs.recommendedTypeChecked,
      // Alternatively, use this for stricter rules
      ...tseslint.configs.strictTypeChecked,
      // Optionally, add this for stylistic rules
      ...tseslint.configs.stylisticTypeChecked,

      // Other configs...
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])
```

### 使用 Jenkins 自动化部署

以下 Jenkinsfile 已包含在仓库根目录，用于在 Windows 节点上完成依赖安装、构建、归档和部署静态资源到指定目录：`Jenkinsfile`。

#### 前置条件
- 安装 Jenkins，且可在 Windows Agent 节点执行构建（具备写入目标部署目录权限）。
- 节点已安装 Node.js (建议 18+)，并可执行 `corepack enable` 以启用 pnpm；或在 Jenkins 中安装 NodeJS 插件并全局提供 Node。
- 节点可访问 Git 仓库；若私有仓库，请在 Jenkins 中配置凭据并在 SCM 中选择。

#### Jenkins 任务配置
1. 新建项 → 选择 Pipeline（流水线），命名如：`demo-deploy`。
2. 在“构建触发器”按需启用（例如：Git Webhook 或 Poll SCM）。
3. 在“流水线”选择“Pipeline script from SCM”。
   - SCM：Git
   - Repository URL：填写你的仓库地址
   - Credentials：选择对应凭据（如需要）
   - Branches to build：`main`（按需修改）
   - Script Path：`Jenkinsfile`
4. 参数化构建（可选）：Jenkinsfile 已定义参数 `DEPLOY_DIR`（默认为 `E:\\jenkins_demo_deploy`）。如需修改默认值，可在构建时传参或在参数化构建中设置默认值。

#### 流水线阶段说明
- Checkout：检出当前分支代码。
- Setup Node & pnpm：显示 Node/npm 版本，启用 corepack 并激活 pnpm。
- Install：`pnpm install --frozen-lockfile`（使用 `pnpm-lock.yaml` 保证可重复构建）。
- Build：`pnpm run build`，产物位于 `dist/`。
- 归档：保存 `dist/**` 作为构建工件。
- Deploy：通过 PowerShell 将 `dist/*` 拷贝到 `DEPLOY_DIR`。

#### 部署目录与静态站点服务
- 将 IIS 站点或 Nginx（Windows 版）根目录指向 `DEPLOY_DIR`，即可对外提供静态站点服务。
- 临时预览可使用 `npx serve` 等本地静态服务工具；生产环境建议使用长期运行的 Web 服务器。

#### 通过 MCP-Jenkins 触发构建（可选）
若你已在 Cursor 配置了 MCP Jenkins（参考 `c:\\Users\\sjk\\.cursor\\mcp.json` 与项目 [mcp-jenkins](https://github.com/lanbaoshen/mcp-jenkins)），可使用工具触发 Jenkins 任务（如 `build_job`）、查看日志（`get_build_logs`）等，以实现从 IDE 内一键发布。


You can also install [eslint-plugin-react-x](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-x) and [eslint-plugin-react-dom](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-dom) for React-specific lint rules:

```js
// eslint.config.js
import reactX from 'eslint-plugin-react-x'
import reactDom from 'eslint-plugin-react-dom'

export default tseslint.config([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...
      // Enable lint rules for React
      reactX.configs['recommended-typescript'],
      // Enable lint rules for React DOM
      reactDom.configs.recommended,
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])
```
qqq