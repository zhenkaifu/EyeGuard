# EyeGuard 护眼应用

> **RevenueCat Shipaton 2026 参赛作品 —— Next Gen Award**
> 用订阅制把"护眼提醒"做成可持续的产品，而不是一次性工具。

## 简介

EyeGuard 针对三个最常见的伤眼场景，用一部手机的前置相机和传感器完成全天候守护：

1. **贴脸刷手机**：CameraX 定时采样前置相机一帧 + ML Kit 人脸检测，估算眼屏距离，低于阈值立即提醒（默认 30cm）；
2. **暗光用眼**：常开光线传感器，环境光低于阈值（默认 50 lux）且亮屏时提醒开灯（10 分钟不重复）；
3. **长时间用眼**：基于 20-20-20 法则，连续用眼满 20 分钟提醒远眺 20 秒，每日亮屏时长与提醒次数落库，首页展示。

变现采用 **RevenueCat 订阅**：免费版包含三大基础检测，Pro 版解锁智能屏幕柔和（护眼遮罩）、自定义阈值、7 天历史图表、每周用眼报告与免打扰时段。

## 功能一览

| 模块 | 免费版 | Pro |
|---|---|---|
| 距离检测（定时拍一帧测距） | ✅ | ✅ |
| 环境光暗提醒 | ✅ | ✅ |
| 用眼时长提醒（20-20-20） | ✅ | ✅ |
| 今日数据（用眼时长 / 过近时长 / 最长连续用眼 / 休息遵守率） | ✅ | ✅ |
| 连续达标 streak | ✅ | ✅ |
| 智能屏幕柔和（悬浮窗暖色遮罩，随环境光/夜间自动调节） | — | ✅ |
| 自定义阈值（距离 / lux / 分钟） | — | ✅ |
| 最近 7 天用眼历史图表 | — | ✅ |
| 每周用眼报告（环比 / 最佳护眼日 / 成就徽章） | — | ✅ |
| 免打扰时段（所有提醒静音） | — | ✅ |

其他能力：前台服务常驻通知（含"暂停 15 分钟 / 1 小时 / 立即恢复""遮罩关 5 分钟"快捷操作）、Quick Settings 遮罩开关、服务被系统回收时的首页修复引导卡片（一键跳电池白名单 / 自启动管理）、开机后一键恢复守护通知、低电量自适应采样间隔、付费墙（月付/年付，真实价格）、恢复购买、中英文切换。

### 截图占位

<!-- TODO: 替换为真实截图
![首页](docs/screenshots/home.png)
![距离提醒](docs/screenshots/distance.png)
![护眼遮罩](docs/screenshots/filter.png)
![付费墙](docs/screenshots/paywall.png)
![设置](docs/screenshots/settings.png)
-->

## 技术架构

```
graph TD
    subgraph UI层（Jetpack Compose / Material 3）
        A[HomeScreen<br/>今日数据 + 7天柱状图 + 修复引导] 
        B[SettingsScreen<br/>距离/光线/用眼/遮罩/DND/语言]
        C[PaywallScreen<br/>RevenueCat offerings 真实价格]
        D[OnboardingPermissionsScreen<br/>相机/通知/悬浮窗引导]
        E[DndScreen]
        F[EyeFilterTileService<br/>快捷设置开关]
        G[WeeklyBarChart<br/>Canvas 自绘]
        H[WeeklyReportScreen<br/>周报 + streak + 成就]
    end

    subgraph Service层（EyeGuardService 单前台服务）
        S1[距离采样调度器<br/>CameraX 一帧即释放 + 低电量自适应]
        S2[LightMonitor<br/>TYPE_LIGHT 常开]
        S3[UsageMonitor<br/>SCREEN_ON/OFF + 20-20-20 + 遵守率]
        S4[EyeFilterController<br/>WindowManager 悬浮遮罩]
        S5[Notifier<br/>常驻通知 + Heads-up 提醒]
        S1 --> S5
        S2 --> S5
        S3 --> S5
    end

    subgraph 系统入口
        B1[BootReceiver<br/>开机后一键恢复守护]
    end

    subgraph 数据层
        D1[SettingsRepository<br/>DataStore Preferences]
        D2[EyeGuardDatabase / Room<br/>DailyUsageEntity]
        D3[DailyUsageRepository]
    end

    subgraph 变现（RevenueCat SDK 10.x）
        R1[BillingRepository<br/>init/offerings/purchase/restore]
        R2[entitlement: pro]
    end

    UI层 --> Service层
    UI层 --> 数据层
    Service层 --> 数据层
    C --> R1
    B -.isPro 门控.-> R1
    Service层 -.DND/阈值.-> R1
```

- **单 Activity + Navigation Compose**；MVVM（`@HiltViewModel` + `StateFlow`）
- **Hilt** 依赖注入：Repository / Monitor / Service 全部可注入
- **版本目录** `libs.versions.toml` 统一管理依赖
- **minSdk 26 / targetSdk 34**，JDK 17，AGP 8.5.2

## 构建

### 环境要求

- Android Studio（建议 Hedgehog+），JDK 17
- Android SDK 34（Build-Tools 34）

### 配置 RevenueCat API Key

在项目根目录创建 `local.properties`（不要提交到版本库）：

```properties
REVENUECAT_API_KEY=goog_xxxxxxxxxxxxxxxxx
```

Key 在编译期注入 `BuildConfig.REVENUECAT_API_KEY`，代码内不硬编码。

### 编译

```bash
./gradlew assembleDebug
```

### RevenueCat 控制台需要创建

1. **Project**：关联 Android App（包名 `com.eyeguard.app`），Google Play 渠道上传 Play 服务账号 JSON；
2. **Entitlement**：id 为 `pro`（与 `BillingRepository.ENTITLEMENT_PRO` 一致）；
3. **Products**：Play Console 建两个订阅 `eyeguard_pro_monthly`（1 个月）、`eyeguard_pro_yearly`（12 个月）并激活定价；RevenueCat 控制台中添加对应 product；
4. **Offerings**：默认 offering 设为 Current，加 `$RC_MONTHLY` / `$RC_ANNUAL` 两个 package。

**测试模式**：Play Console「许可测试」账号 + RevenueCat 控制台 Customers 页手动授予/撤销 `pro` entitlement 即可联调。

## 距离估算算法

针孔相机模型 + 相似三角形：

```
distance = (真实脸宽 × 焦距px) / 人脸框宽px
焦距px   = (图像宽px / 2) / tan(HFOV / 2)
```

- 真实脸宽取成年人先验 14cm；HFOV 默认 67°；
- 输出仅在 **20–50cm** 范围内可信，超出返回 null；
- **精度限制**：近距离人脸易出画漏检；>50cm 框宽误差放大（±10cm）；儿童脸偏小导致系统性高估；暗光/侧脸/眼镜增大误差。定位是"量级正确的提醒"，不是测量仪器。校准可调整 `DistanceEstimator.DEFAULT_FACE_WIDTH_CM` 或传入实测 HFOV。

## 已知限制

- **iOS 已有系统级"屏幕距离"功能**（iPhone/iPad 利用原深感相机），iOS 版的距离检测卖点不大，本项目聚焦 Android；
- **国产 ROM 后台限制**：MIUI / EMUI / ColorOS 需手动允许"自启动"并把省电策略设为"无限制"，否则前台服务会被回收；不采用灰色保活手段（市场拒审风险）；
- **护眼遮罩**：截图/录屏会带暖色调；银行、支付类 App 的防截屏（FLAG_SECURE）页面会临时屏蔽遮罩，属系统行为；
- **亮灭屏广播**：Android 8+ 只能运行时注册，服务被回收期间用眼统计中断；
- Room 开发期使用 `fallbackToDestructiveMigration`，发布前需替换为正式 Migration；
- 距离估算受设备前置相机参数差异影响，未做逐机型标定。

## Roadmap

- **家长端**：远程查看孩子用眼数据、距离提醒周报（多设备绑定）
- **全屏休息遮罩**：提醒时短暂全屏覆盖（可跳过），强制远眺 20 秒
- **Edge Lighting 提醒**：息屏曲面光效提醒，避免通知横幅打断当前操作

## License

MIT License

Copyright (c) 2026 EyeGuard Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
