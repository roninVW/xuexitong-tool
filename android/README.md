# 作业板 · Android 客户端

Kotlin + Jetpack Compose。视觉按**纸感令牌**实现：浅纸 #F6F0E2 / 深纸 #151517、卡片 #FFFDF7 / #1E1E21、墨色 #2B2419 / #EDEDEF；**默认唯一基色 #C4763F**，考试色 / 荧光色 / 状态色 / 科目点全部由它 HSV 派生。Material 3 只承担控件与交互。顶部高斯模糊使用平台 `RenderEffect.createBlurEffect`；可选液态玻璃材质使用 Backdrop 的官方折射管线，两者独立。Dock 是 MAIN 层的共享组件，不参与页面切换动画。`compileSdk 36 / targetSdk 36 / minSdk 26`（目标 Android 16）。

> v0.51.0：新增独立全局液态玻璃材质与透明度/折射度调节；作业页二级菜单采用整体单块玻璃面板，保留连续展开与筛选动画；各页顶部高斯模糊统一。一言按实际可用空间调整位置。作业截止提醒支持开关、提前时间、通知权限与点击详情，排除已完成/过期作业。保留原主题、配色及默认材质。versionCode 48；Release APK 使用现有签名，可覆盖更新。

> v0.3.0：**回滚 v0.2.0 的 Material 3 重写**，回到纸感视觉；毛玻璃重写为真正生效的原生实现；课表从占位改成可导入。
> v0.6.0：列表按顺序逐条**从左飞入**（正弦缓动）；**跳转学习通**改成四级降级 + 可见反馈；应用内 WebView 顶部加状态栏/挖孔遮挡。
> v0.7.0：旧的模糊实现全部删除，改接 Haze 1.5.4；页面切换改为 M3 fade through；玻璃分支（flavor `glass`）废弃并移出源码集。
> v0.8.0：模糊**全栈换成 QmBlurView**（软件截屏 + native 高斯，minSdk 21）；**页面转场重写**为两套 M3 转场（Tab 用 fade through、二级页用 shared axis X），并去掉整页缩放造成的黑边。
> v0.9.0：卡片改成**从屏幕外飞入**并提速（300ms / 26ms 间隔）；新增**可调贝塞尔**（设置里 4 个滑杆 + 曲线预览 + 3 个预设）；新增**背景壁纸**（图片 / GIF / 视频）与**卡片磨砂**；一言改成整行两行显示。
> v0.10.0：模糊去掉第三方/native 实现；新增自检面板与崩溃日志。
> v0.11.0：模糊改用**Android 平台原生 `android.graphics.RenderEffect`**，并修掉一直写反的平移符号（这才是「模糊没实现」的真因）；删除全部自研动效。
> v0.12.0：**联网查证后重写**。毛玻璃改为两级模糊 + AGSL 材质链；M3 动效按源码 token 从底层重写。
> v0.13.0：**全栈 M3 视觉重写**（保留作业卡片 / 看板顶栏 / Dock 胶囊外形四个页面的组件、色板、字阶、形状、动效全部换成官方 M3）；新增 Android 12+ 动态取色。
> v0.14.0：三区域独立局部动态毛玻璃（已被 v0.15.0 规格取代）。
> v0.15.0：**按设计规格回滚并定稿**。纸感令牌 + 单基色 HSV 派生；模糊改回纯平台 RenderEffect；顶栏 42dp 类型行 + 两个二级菜单；卡片静态 58% 左渗渐变；动效 fade 220 + slide 300。
> v0.16.0：**采样层补全**（含页面底色/壁纸，穿过玻璃的元素不再漏糊）；右上菜单改 M3 `ModalBottomSheet + FilterChip` 但配色仍用纸感令牌；**Dock 移出页面转场**、转场方向按 Tab 序号；统一字阶 + 宋体 Async 加载；冷启动改为 IO 异步初始化 + 首帧 Splash + 窗口纸色背景。
> v0.19.2：**WebView 触控根治 —— 搬出 Compose**。之前 WebView 用 `AndroidView` 嵌在 `NavHost` 里，触摸要先过 `AndroidComposeView` 的 pointer input 再转发给内嵌 View，只要外层有转场变换/采样层，视觉位置与命中区域就可能错开，改布局、改转场都没根治。现在新增独立原生 `WebActivity`（`setContentView(WebView)`，纯 View 层级，触控 1:1），删掉 Compose 的 `Routes.WEB` / `WebScreen`；顶栏改成原生 LinearLayout（返回 / 刷新 / 点不动? / 外部打开），JS alert/confirm/prompt 用原生 `AlertDialog`；UA 去掉 `; wv` 标记，避免学习通按「非浏览器」罩上全屏引导层；「点不动?」会检测并临时让开 fixed 全屏浮层（只改 `pointer-events`，不动布局），弹窗里同时显示命中元素与 UA 便于定位；卡片右下角恢复「学习通」按钮，直接跳学习通 App（失败回落到这个页面）。
> v0.19.1：**WebView 触控修复 + 大号一言收尾**。① WebView 接管 `onJsAlert / onJsConfirm / onJsPrompt`（之前设了 WebChromeClient 但没实现，页面的 confirm 弹不出来，点「交卷」像没反应）、`setSupportMultipleWindows(false)` 让 window.open 走本 WebView、补 `isFocusable / isFocusableInTouchMode / requestFocus`、入口加载从组合期的 factory 挪到 `LaunchedEffect`（组合期写 state 会打断输入）；② 大号一言删掉「点一下换一句」提示，加引号与手绘短线装饰，**触控区只包文字并做 16dp 圆角**；③ 空白空间不再整块可点。
> v0.19.0：**玻璃实时跟随切页动画 + 空白处大号一言 + 去掉卡片重复按钮**。① `PageMotion`（snapshot state）+ 切页改为逐帧推进：每帧把页面位移写进去，`GlassSurface(outsidePage = true)`（Dock）采样点减去位移，所以 Dock 里的模糊会跟着页面一起滑，而不是定格贴图；② 作业不足一屏 42% 时，一言放大（宋体 20sp 居中）放进列表下方空白，高度按「列表末尾 → Dock 上方」算，不会被 Dock 压住；作业多时仍留在顶栏；③ 卡片右下角的「打开」按钮删除（整卡点击进详情，详情里已有应用内打开 / 学习通 App）。
> v0.18.2：**下拉刷新**。作业板与统计页用 M3 `PullToRefreshBox`：下拉即触发一次同步，指示器用纸感 card/accent 配色并避开顶栏落到内容区顶部；刷新中复用 `vm.syncing`，不会重复触发。
> v0.18.1：**WebView 带登录态 + 跳转改路**。① 应用内 WebView 创建前把保险箱里的 Cookie **按域名补进 CookieManager**（只补缺，不覆盖 WebView 里更新的），考试页（`mooc2-ans`）不再白屏/跳登录；域名统一 `mooc1-api → mooc1`；加载完 `requestLayout()`、JS 检测正文过短时给出「重试 / 用浏览器打开 / 学习通 App」兜底页；② 卡片「打开」改为直接进应用内页面（一步到作业，不用粘贴链接）；③ 「学习通 App」先试 `cxstudy://` 深链，失败再交给 http / 复制链接。
> v0.18.0：**作业 / 考试分别配色**。`AccentColors` 增加 `examOverride`，`paperTokens(..., exam=)` 接收独立考试色；设置页颜色卡片拆成「作业色（基色）」+「考试色」两组，考试色可单独挑 6 个候选色或恢复「自动」（按作业色派生）；作业卡片光效、考试徽标、已过期状态用考试色，「紧急」荧光仍从作业色派生。
> v0.17.3：**修 WebView 与分数**。① 页面切换时钟初值改为 1（原来初值 0，冷启动 / 从 WebView 返回时页面被重建、`LaunchedEffect` 直接 return，页面永远停在 alpha=0 → “主页面元素不显示”）；② WebView 关掉 `loadWithOverviewMode`、`textZoom` 固定 100、加载完 `requestLayout()`、离开时 `destroy()`，并修掉按 `view.url` 比较导致的入口页反复重载；③ 分数：考试列表不再要求 `status==4`、按「纯数字单元格」兜底找分数列，作业列表已批阅时也从列表抓分数；已批阅但没分数的任务会补抓一次详情（6 小时内不重复）；卡片只要有分数就显示，不再卡 `status==GRADED`。
> v0.17.2：**顶栏统一**。新增 `PaperTopBar`（标题行固定 44dp、左内边距固定 16dp、字号固定 `PaperType.screenTitle` 宋体 20sp 粗体），看板/课表/统计/设置/登录五处顶栏全部换用它；右上筛选面板删掉与左侧标题菜单重复的「类型」分组（类型只在标题菜单里选，面板只留状态 + 科目）。
> v0.17.1：**修玻璃错位**（设置/统计/课表的采样层原来挂在 `.padding(inner)` 之后，层原点被顶栏顶偏，玻璃里显示的是错位内容；改为整页根部按**窗口坐标**录制，并只让前台页录制、没录过时玻璃不画内容）；**转场重做曲线**（位移 emphasized decelerate、退场 accelerate、缩放 0.94→1 带轻微回弹、纵向 12dp 归位，340ms）。
> v0.17.0：**手账风格（可切换）**。新增 `ThemeStyle.PAPER / JOURNAL`，设置页「界面风格」一键切换；手账 = 纯色米黄纸底 + 虚线卡片 + 纸胶带 + 手绘波浪分隔 + 手写感标题下划线 + 更大留白，覆盖看板/课表/统计/设置/登录/详情弹层/筛选面板/Dock；右上筛选面板补 `类型` 分组，类型/状态/科目 chip 全部带数量；新增「玻璃透明度」滑块（0 = 自动，随模糊强度 0.94→0.58）；Dock 选中胶囊改为与大胶囊同心圆角（内圆角 = 外圆角 − 7dp）。背景只保留纯色，不铺任何纹理。

---

## 1. 现在的 UI

| 区域 | 实现 |
|---|---|
| 令牌 | 浅纸 #F6F0E2 / 深纸 #151517；卡片 #FFFDF7 / #1E1E21；墨色 #2B2419 / #EDEDEF；默认基色 #C4763F |
| 配色 | 唯一基色经 `shift()` HSV 派生考试色、荧光三档、四态状态色、13 个科目点；无分类硬编码 |
| 顶栏 | 42dp 类型行（左「作业 ⌄」作业/考试 DropdownMenu，右「未完成 ⌄」→ M3 ModalBottomSheet 状态+科目筛选，颜色仍为纸感）+ 同步/一言行；玻璃底 |
| 任务卡片 | 紧凑横向；58% 宽度从左侧渗入的**静态横向渐变** + 3dp 圆角亮边；**严禁顶部受光**；类型/课程元信息 + 小号「打开」按钮 |
| 底部 Dock | 33dp 胶囊，220ms FastOutSlowIn 指示器过渡；玻璃底；**在 M3PageSwitcher 之外，切页时静止**；选中胶囊内圆角 = 33 − 7 = 26dp，与大胶囊同心 |
| 设置 | 深浅色、**唯一基色色板**、**模糊强度 0–40dp**、壁纸、一言、通知、退出登录、自检 |
| 其它页面 | M3 官方控件（TopAppBar / Card / ListItem / SegmentedButton / Switch / Slider），颜色由纸感令牌映射 |

### 1.1 M3 动效：按源码 token 从底层重写

查证来源：Material 3 motion tokens（`material-web` 的 easing/duration token 表、
`androidx.compose.material3.tokens.MotionTokens`）与
[Compose M3 motion 规范整理](https://github.com/rshzrh/ui-design-skills/blob/main/skills/ui-android/motion.md)。

之前只有两条 `fadeIn/fadeOut`，时长和曲线都不成体系，所以「没完全实现」。现在
[`ui/theme/M3Motion.kt`](app/src/main/java/com/xxt/board/ui/theme/M3Motion.kt) 把 token 表补齐：

| 类别 | 值 |
|---|---|
| Duration | short1 50 / short2 100 / short3 150 / short4 200 / medium1 250 / medium2 300 / medium3 350 / medium4 400 / long1 450 / long2 500 / long3 550 / long4 600 / extraLong1-4 700-1000 ms |
| Easing | standard `(0.2,0,0,1)`、standardDecelerate `(0,0,0,1)`、standardAccelerate `(0.3,0,1,1)`、emphasized `(0.2,0,0,1)`、emphasizedDecelerate `(0.05,0.7,0.1,1)`、emphasizedAccelerate `(0.3,0,0.8,0.15)` |

[`M3Transitions.kt`](app/src/main/java/com/xxt/board/ui/theme/M3Transitions.kt) 实现四类原语：

| 原语 | 用在哪 | 规格 |
|---|---|---|
| fade through | 底部 Tab 之间、登录 → 主页 | 旧页 90ms（EmphasizedAccelerate）→ 新页 210ms（EmphasizedDecelerate）+ 0.92→1.0 缩放，两段不重叠 |
| shared axis X | 进 / 出应用内 Web | medium2 300ms，位移 1/12 屏宽 + 交叉淡入淡出 |
| fade | 类型菜单 / 筛选面板显隐 | short4 200ms + standard，0.92→1.0 |
| state change | dock 指示器与图标配色 | `animateColorAsState` + short4 + standard |

Tab 切换由 `M3PageSwitcher` 自己用毫秒时钟驱动（两槽 + `SaveableStateHolder`），
**不依赖 NavHost 的 popUpTo**：时钟跑在 `M3FixedMotionScale` 下强制 1×，
这样 ROM 把「动画程序时长缩放」设成 0 时也不会瞬间完成。详情弹层、对话框、
开关、滑块、涟漪仍用 M3 组件自带动效。**列表依然静态，没有飞入。**

### 1.5 背景壁纸与卡片磨砂

- **壁纸**：设置 → 背景，可选图片 / GIF / 视频。选中的文件会**复制到 `filesDir/wallpaper/`**（Photo Picker 的 URI 授权重启就失效，不能直接存 URI）。
  - 图片、GIF 走 `ImageView.setImageURI`（GIF 在 API 28+ 由 `AnimatedImageDrawable` 自动播）。
  - 视频走 `TextureView + MediaPlayer`，静音循环。
- **页面让位**：四个一级页面用 `Modifier.pageBackground()`——有壁纸时不画纸色，壁纸才透得上来；壁纸层本身压一层 34%/46% 纸色 scrim，保证正文可读。
- **卡片磨砂**：卡片不挂 BlurView（那样每帧要重绘 N 次 decor，必卡）。做法是壁纸层维护一张 **72px 宽的低分辨率快照**，卡片在自己的 `drawWithCache` 里按 `positionInRoot` 把它贴到对应位置，再压 72% 纸色：
  ```kotlin
  drawImage(wpImage, dstOffset = IntOffset(-origin.x, -origin.y), dstSize = wpScreen, filterQuality = Low)
  drawRect(p.card.copy(alpha = 0.72f))
  ```
  视频每 700ms 用 `TextureView.getBitmap(72, 140)` 刷新一次快照。

### 1.3 跳转学习通为什么之前不生效

旧实现只有一条路：`ACTION_VIEW + setPackage("com.chaoxing.mobile")`。学习通并没给大多数作业页注册 http 过滤器，`resolveActivity` 返回 null → 直接落到应用内 WebView，看起来就是「点了没反应」。

现在四级降级，每级都有 Toast：

1. 学习通处理 `https://mooc1.chaoxing.com/...`（部分版本注册了域名，可直达）
2. 同一链接换 `http://` 再试
3. `getLaunchIntentForPackage` 直接拉起学习通首页，同时把链接写进剪贴板
4. 都不行才回落到应用内 WebView

> `mooc1-api.chaoxing.com`（接口域名）会先替换成 `mooc1.chaoxing.com`（网页域名），否则 App 和 WebView 都打不开。
> 清单里必须有 `<queries><package android:name="com.chaoxing.mobile"/></queries>`，否则 Android 11+ 看不到已安装的学习通。

### 1.4 WebView 顶部遮挡

`enableEdgeToEdge()` 之后 WebView 会画到状态栏下面，作业页自己的标题栏正好被前置摄像头挡住。现在整块 WebView `padding(top = safeDrawing.top)` 下移，空出来的那一条用 `p.paper` 遮挡条盖住（底边 1dp 玻璃线），`safeDrawing` 同时包含状态栏和挖孔高度。另外补了 `WebViewClient`（链接留在应用内）和 `BackHandler`（返回键先退网页历史）。

---

## 2. 原生高斯模糊（规格 §5）

**只用 `android.graphics.RenderEffect.createBlurEffect`，可链 `createColorFilterEffect` 做饱和增强。**
禁止 Haze / 任何第三方模糊库；禁止 AGSL / RuntimeShader；不截屏、不 PixelCopy、不对整个 Activity 设 RenderEffect。

### 2.1 采样链路

```
backdropSource()  →  GraphicsLayer.record { 主内容 }
                  →  drawLayer(source)                     原位绘制（清晰）
GlassSurface      →  clip(顶栏矩形 / Dock 胶囊)
                  →  graphicsLayer { renderEffect = blur + ColorMatrix }
                  →  translate(-(G-L)) { drawLayer(source) }   栏后真实内容的副本
                  →  tint(alpha) + 前景文字图标（在副本之外）
```

- **绝不把 RenderEffect 挂在纯色背景上**：挂 effect 的那一层里只有 `drawLayer(source)`，即栏后的真实内容。
- 只在顶栏矩形与 Dock 胶囊裁剪区域内重绘副本，区域外画面保持清晰。
- 负号平移（v0.11 修掉的符号 bug）：玻璃在 `(Gx,Gy)`、采样层在 `(Lx,Ly)`，要显示同一窗口坐标必须 `translate(-(G-L))`。
- `onGloballyPositioned { positionInRoot() }` 每帧更新，滚动时顶栏 / Dock 背景同步。
- 半径不变复用同一个 RenderEffect，不每帧重建 Shader；一页只录一次采样层。

### 2.2 参数与降级

| 项 | 值 |
|---|---|
| 强度 | 0–40dp，默认 **28dp**（设置页滑杆） |
| 玻璃透明度 | `0.94 − r/40 × 0.36` → **0.94 线性到 0.58** |
| 饱和增强 | `ColorMatrix.setSaturation(1.18)` 链在模糊之后 |
| Android 12+ | 原生 `createBlurEffect` |
| 旧版本 | **同布局**半透明纸面（只是不画模糊副本，布局/文字/点击完全不变） |

设置 → 自检：`引擎` 一行会显示当前走的是原生还是降级路径。

---

## 3. 课表导入

| 格式 | 解析 |
|---|---|
| `.ics` | 取 `SUMMARY / LOCATION / DTSTART / DTEND / RRULE`；起止时间按常见作息映射成节次；`COUNT`/`UNTIL` 换成周次 |
| CSV / TSV | 有表头时按关键字识别列（课程/星期/节次/周次/地点/教师），无表头按固定列序 |

- 周次表达式：`1-16`、`1-16单`、`2-16双`、`1,3,5-8`。
- 存储：Room `schedule` 表，数据库 1→2 迁移（不动 `tasks` / `status_history`）。
- 入口：课表页右上角菜单（导入文件 / 粘贴文本 / 清空课表）+ 空态按钮。
- 示例：[`dist/课表导入示例.csv`](../dist/课表导入示例.csv)。

---

## 4. 构建

```powershell
cd android
powershell -ExecutionPolicy Bypass -File .\build.ps1                  # assembleDebug
powershell -ExecutionPolicy Bypass -File .\build.ps1 -Task compileDebugKotlin
```

> flavor 已删除，恢复成单个 `assembleDebug`。

脚本会设好 `JAVA_HOME` / `ANDROID_HOME` / `ANDROID_USER_HOME` / `GRADLE_USER_HOME`，并强制
`-Pkotlin.compiler.execution.strategy=in-process`。

> 为什么强制 in-process：本机工程路径含**空格 + 中文**，Kotlin 独立守护进程会把源文件路径编码成
> `u5B66u4E60…`，报「source file not found」。放在纯 ASCII 路径下不需要这个参数。

**本机实测**

```
BUILD SUCCESSFUL
app-debug.apk  → dist/xxt-board-v0.19.2-debug.apk
```

体积涨到 21 MB 是因为内置了 Noto Serif SC（11.7 MB）。标题的宋体不能靠系统字体：国产 ROM 的 `serif` 基本都会退回黑体，风格会垮。介意体积的话，用 `pyftsubset` 裁到 3500 常用字可以降到 ~3 MB（代价是生僻字会缺字）。

工具链：JDK 21.0.13 / Gradle 8.11.1 / AGP 8.9.1 / Compose 1.7.8 / Material3 1.3.1。

---

## 5. 目录

```
app/src/main/java/com/xxt/board/
├─ XxtApp.kt                  Application + 手写 DI
├─ MainActivity.kt            单 Activity + NavHost + 四个 Tab + 跳转学习通 + WebView 兜底
├─ data/
│  ├─ Models.kt               Course / Task / 四态 / 科目
│  ├─ Db.kt                   Room：tasks / courses / status_history / schedule（v2 迁移）
│  ├─ Schedule.kt             课表实体、DAO、CSV/TSV/ICS 解析、周次判定
│  ├─ Settings.kt             DataStore：模糊强度、深浅色、作业/考试色、一言、通知
│  ├─ SecureStore.kt          Keystore AES-GCM 存 Cookie
│  ├─ ChaoxingHttp.kt         OkHttp + Cookie 注入
│  ├─ ChaoxingSource.kt       课程 / 作业 / 考试 / 详情
│  ├─ Parsers.kt              Jsoup 解析（改版只改这里）
│  └─ SubjectClassifier.kt    14 类关键词规则
├─ domain/SyncEngine.kt       同步编排 + 四栏分类 + 状态迁移推断提交时间
└─ ui/
   ├─ Navigation.kt           AppTab 枚举
   ├─ theme/Theme.kt          纸质令牌（浅/深两套）+ 宋体 + 科目色 + M3 桥接配色
   ├─ theme/M3Motion.kt       M3 源码 token：完整时长表 + 六个 easing
   ├─ theme/M3Transitions.kt  fade through / shared axis X / fade + 页面切换引擎
   ├─ components/
   │  ├─ AppIcons.kt          线性图标（24 网格 / 1.7 线宽 / 圆头）
   │  ├─ Blur.kt              原生 RenderEffect：BackdropHost / GlassSurface
   │  └─ Components.kt        卡片 / 荧光笔徽章 / 分组头 / pill / dock / 空态
   ├─ board/BoardScreen.kt    看板（顶栏 + 类型菜单 + 筛选浮层 + 列表）
   ├─ board/TaskDetailSheet.kt
   ├─ schedule/ScheduleScreen.kt  课表周视图 + 导入
   ├─ stats/StatsScreen.kt
   ├─ settings/SettingsScreen.kt
   ├─ login/LoginScreen.kt
   ├─ glass/GlassEffect.kt    两级模糊 + AGSL 材质（vibrancy/噪声/色散/边缘光）
   └─ wallpaper/Wallpaper.kt  壁纸层（图片/GIF/视频）+ 卡片磨砂快照 + 文件导入

archive/glass-ui/             玻璃分支源码（v0.7.0 废弃，已移出源码集，可随时恢复）
app/build/outputs/apk/debug/  唯一产物
```

---

## 6. 刻意决定

1. **不做 destructive migration**。`status_history` 是提交时间的唯一来源，清库等于废掉这个能力。
2. **详情只补需要的**：仅「未交/过期且缺截止时间」的条目，单次最多 20 条。
3. **跳转不用外部浏览器兜底**，一律走内置 WebView（否则没有 Cookie 要重新登录）。
4. **登录不碰密码加密**：WebView 里用户自己登，只收 Cookie。
5. **一言走在线 API**，不打包语料库（官方 sentences-bundle 是 AGPL）。
6. **统计不做假进度**：学习通不保证给发布时间，就不画"时间已过去 x%"。
7. **M3 只做动效**：视觉全部手写，避免又滑回"系统控件拼装"的样子。

---

## 7. 已知风险

- 学习通改版 → `Parsers.kt` 失效。缓解：选择器集中 + HTML 快照回归测试（待补）。
- Cookie 失效 → 顶栏出现「登录已失效」+ 空态里给「重新登录」。
- 部分厂商 ROM 的「性能模式」可能关掉 RenderEffect → 只剩实色玻璃，功能不受影响。
- 当前环境没有模拟器/真机，**UI 与模糊只做到编译与静态检查验证**，实际效果需要在设备上确认。
- KSP 在本机偶发 `kspDebugKotlin FAILED`（路径含中文 + 沙箱临时目录），重跑一次即可。

## 界面风格（v0.17.0）

| | 纸感 `paper` | 手账 `journal`（默认） |
| --- | --- | --- |
| 背景 | 纯纸色 `#F6F0E2` / `#151517` | 纯米黄 `#FAF3E1` / `#16150F`（**纯色，无纹理**） |
| 卡片 | 5dp 圆角 + 1dp 实线描边 | 11dp 圆角 + 1dp **虚线**描边 |
| 分隔 | `HorizontalDivider` 直线 | `JournalDivider` 手绘波浪线 |
| 小节标题 | 纯文字 | 文字背后贴一段**纸胶带** |
| 页面标题 | 居中宋体 | 宋体 + 手绘下划线（`JournalTitle`） |
| 留白 | 卡片间距 7dp | 9dp |

两套风格共用同一套布局与业务逻辑，切换只换 `PaperTokens`（颜色/圆角/装饰），
不新增任何页面或交互。相关实现集中在 `ui/theme/Journal.kt`：
`Modifier.journalCard` / `JournalDivider` / `WashiTape` / `JournalTitle` / `JournalSectionLabel`。

## v0.20.0 · Dock 拉伸回弹

只改了 `PaperDock` 的选中切换动画，布局 / 图标 / 尺寸 / 颜色 / 圆角 / 点击逻辑全部未动。

| 阶段 | 时长 | 曲线 | 形变 |
|---|---|---|---|
| 拉伸 | 120ms | `LinearOutSlowInEasing` | scaleX → **1.16**，scaleY → **0.94** |
| 回弹 | ~250ms | `spring(dampingRatio = 0.34 / 0.45, stiffness = MediumLow)` | 回到 1.0，带极轻微反向过冲 |
| 退出选中 | 140ms | `tween` | 强制归位，不残留形变 |

实现要点：

- 形变挂在整项（含药丸 indicator）的 `graphicsLayer` 上，`transformOrigin = (0.5, 0.5)`，所以是「胶囊被拉长」而不是图标单独缩放。
- 连续快速点击：`LaunchedEffect(active)` 随选中态变化自动取消上一段协程，且每次进入先 `Animatable.stop()`，不会堆积或停在中间值。
- 旧项与新项同时过渡：旧项走 140ms 归位，新项走「拉长 → 弹簧」，两者时间重叠，视觉上是连贯的一次切换。
- indicator 颜色仍用 220ms `FastOutSlowInEasing` 过渡，与形变叠加。

> 构建备注：中文路径会让 Kotlin daemon 把路径转义成 `u5B66...` 导致 `source not found`，
> 已在 `gradle.properties` 里加 `kotlin.compiler.execution.strategy=in-process` 解决。

## v0.21.0 · 作业卡片火焰边缘（可选）

设置 → 内容 → **作业卡片火焰**，默认关闭。

### 视觉

- 只有作业卡片（`TaskType.WORK`）有；考试卡片不受影响。
- 火焰只出现在距边 **18dp** 以内，中心完全透明，文字/图标/按钮不受遮挡。
- 颜色沿横向过渡：深红 `#7A0B00` → 鲜红 `#E01B0B` → 橙 `#FF7A18` → 金 `#FFD24A`。
- 三层叠加：低频噪声（形态缓慢变形）+ 高频噪声（内部纹理流动）+ 正弦明暗扰动（呼吸感）。
- **没有**顶部白色高光、没有扫光、没有大面积光晕——火焰只在边缘一圈。

### 实现

`ui/components/Flame.kt`，一个 **AGSL RuntimeShader**：

| 项 | 做法 |
|---|---|
| 形态 | 2 层 fbm 噪声，x 方向随时间负偏移 → 从左向右流动 |
| 明暗 | `sin(t*5.5 + x*11 + n1*7)`，避免机械循环 |
| 边缘约束 | 像素级 `min(到四边距离)` + `smoothstep(0, 18dp, d)`，中心 alpha=0 |
| 绘制 | `drawWithCache` 缓存 shader，每帧只 `setFloatUniform` + 一次 `drawRect` |

### 性能

| 措施 | 实现 |
|---|---|
| 单一动画源 | 整个列表共用 `rememberFlameClock`，30fps，不是每卡一个动画 |
| 不重组 | 时间通过 `() -> Float` 传入，在 draw 阶段读取，只触发重绘 |
| 对象复用 | shader / Paint / Brush 在 `drawWithCache` 里创建一次，尺寸变化才重建 |
| 不可见即停 | LazyColumn 回收屏外卡片，draw 自然停止 |
| 后台即停 | `repeatOnLifecycle(RESUMED)`，退后台协程挂起 |
| 关闭即停 | 开关关闭时协程不启动、火焰层不进入组合（不是画了再隐藏） |
| 降级 | API < 33 无 RuntimeShader，退化为四条边的渐变带（仍有呼吸） |

### 开关

- `AppSettings.flameEffect`（DataStore `flame_effect`），默认 `false`。
- 切换立即生效：开启 → 时钟启动、图层出现；关闭 → 协程取消、图层移除。
- 持久化，下次进入保持上次选择。

## v0.22.0 · 转场 / WebView 触控 / 设置精简

| 项 | 改动 |
|---|---|
| 页面转场 | `PageSwitchMs` 340 → **210ms**；位移 14% → **10%** 屏宽；缩放 0.94 → **0.972**；去掉回弹曲线（`SoftOvershoot` → `EmphasizedDecelerate`），淡入 50% → 42% 完成。整体更短、更轻，不再有「顿一下」 |
| 应用内 WebView 触控 | 配置**对齐登录页**：不改 `userAgentString`、不限制缩放、去掉 `onPageFinished` 注入的 viewport 脚本。登录页触控正常，说明问题出在这些「优化」上 |
| 设置页 | 删除「背景」整段（壁纸导入 / 卡片磨砂）和「自检」整段（设备 / 动画缩放 / 模糊引擎 / 上次崩溃）；同时移除 `onWallpaper`、`onCardBlur` 参数与壁纸选择器 |

> 设置页现在只剩：外观 → 内容 → 账号 → 关于。

## v0.23.0 · 转场推倒重做 / WebView 详情页 / 卡片去重

### 删掉的旧动画

| 文件 | 删除内容 |
|---|---|
| `ui/theme/M3Transitions.kt` | 整个 `M3PageSwitcher`（自建毫秒时钟 + 逐帧 `snapTo` + 位移/缩放/alpha 三层叠加 + `PageMotion` 采样补偿）、`PageSwitchMs`、`SoftOvershoot`、两份 easing 副本 |
| `WebActivity.kt` | 整个 Activity（工具栏 + 原生 WebView 布局） |
| `ui/board/TaskDetailSheet.kt` | 弹窗式详情（与卡片点击重复的「打开」入口） |

清理后 `grep` 确认：`M3PageSwitcher` / `PageSwitchMs` / `SoftOvershoot` / `WebActivity` / `TaskDetailSheet` 均为 0 引用，不存在新旧动画并存。

### 新转场（统一由 `M3Transitions` 提供）

全部基于 `M3Motion.kt` 的官方 token，不再有自定义时钟。

| 场景 | 规格 |
|---|---|
| 同级 Tab（Dock 四页） | **fade through**：出场 90ms（fade + scale→0.98）先结束，进场 210ms（fade + scale 0.92→1），两段不重叠 |
| 层级导航（登录→主页、主页→详情） | **shared axis X**：进 300ms emphasized-decelerate + 1/8 屏宽位移；出 250ms emphasized-accelerate + 1/16 位移；淡入提前 30ms，避免先黑后亮 |
| 返回 | 位移方向自动反向（`popEnterTransition` / `popExitTransition`） |
| 快速连点 | 交给 `AnimatedContent` 的 targetState 串行化 + Navigation 的转场管理，不再手写取消逻辑 |

### WebView 详情页（`ui/web/WebScreen.kt`）

- `Scaffold` + `TopAppBar`（返回 / 刷新 / 浏览器打开）+ 全屏 WebView，insets 由 Scaffold 处理。
- 加载态：主题色遮罩 + 圆形进度，`AnimatedVisibility` 220ms 淡出；顶部另有 1–3px 细进度条。
- 返回键：`BackHandler` → 先 `canGoBack()` 回退网页历史，退无可退才退出页面。
- `AndroidView.factory` 只创建一次，`onRelease` 里 `stopLoading + destroy`，避免重复初始化与内存泄漏。
- 站内链接留在 WebView，站外交系统浏览器。
- WebView 配置与登录页一致（不改 UA、不限制缩放），触控表现应与登录页相同。

### 卡片去重

删除详情弹窗后，卡片只剩两个**职能不同**的入口：

| 入口 | 行为 |
|---|---|
| 点击卡片任意位置 | 应用内打开作业详情（WebScreen） |
| 右侧「学习通」按钮 | 跳官方 App；未安装时回落到应用内 WebScreen |

原来的「点击卡片 → 弹窗 → 弹窗里再点『应用内打开』」和卡片右侧「学习通」构成重复路径，已消除。

## v0.24.0 · WebView 闪退 / Dock 动画 / 方向转场

> 本机 `adb devices` 为空，**没有拿到 Logcat**。以下根因来自代码级排查，不是猜测。

### 闪退根因（已修）

**Web 路由被错误地注册在 `composable(Routes.LOGIN)` 的内容 lambda 内部**（多了一个 `}`）。
登录页不显示时，这个路由根本没有进入 NavGraph，`nav.navigate("web?...")` 找不到目的地，
直接抛 `IllegalArgumentException: Navigation destination ... cannot be found` → 主线程崩溃。
这正是「点卡片就闪退」的表现。

修复与加固：

| 问题 | 处理 |
|---|---|
| 路由嵌套 / 多余大括号 | 重排 NavHost，WEB 与 LOGIN 平级 |
| URL 直接拼进 route（含 `&` `?` 中文） | 改 **Base64(URL_SAFE)** 传参，`decodeArg` 容错解码 |
| `factory` 里 `webView = this`（组合期写状态） | 改成普通持有对象 `WebViewHolder`，不参与重组 |
| `onRelease` 直接 `destroy()`（View 可能仍附着） | 先 `stopLoading + about:blank + 移除 client`，再 `post` 到下一帧 `destroy()`，全程 `runCatching` |
| 渲染进程被杀（WebView 闪退最常见原因） | 重写 `onRenderProcessGone` 返回 `true`，切错误态，不让系统杀进程 |
| 加载失败白屏 | `onReceivedError`（主框架）→ M3 错误态 + 重试 / 浏览器打开 |
| 连点卡片重复入栈 | `navigate { launchSingleTop = true }` |
| WebView 进 alpha 图层（部分 ROM 会崩） | 详情页转场改为**纯平移**（`webEnter/webExit/webPopEnter/webPopExit`），不做透明度 |

### Dock 动画修复

旧实现两个问题：① 每个 item 各自 `remember(tab) + LaunchedEffect(active)`，靠位置记忆化，
重组时容易重建/打断；② 形变加在**整个 item（含药丸背景）**上，被 Dock 的圆角裁剪，
1.16 的幅度几乎看不出来。

新实现：

- 形变状态提升到 **Dock 级**（`AppTab.entries.associateWith { Animatable(1f) }`），item 重组不会重建。
- **单个 `LaunchedEffect(selected)`** 统一驱动：先复位其余项，再给新项跑「拉伸 → 弹簧」。
- 形变只作用于**图标 + 文字**（`graphicsLayer`，不改布局、不影响点击热区），不会被裁剪。
- 幅度提到 **1.28 / 0.88**，拉伸 110ms + `spring(0.36, MediumLow)` 回弹，肉眼可见且有惯性。
- 快速连点：`LaunchedEffect` 自动取消上一段，且每次进入先 `stop()`，不会叠加或停在中间值。

### 方向转场

| 场景 | 行为 |
|---|---|
| Tab 右切 | 新页从**右侧整屏**飞入，旧页向左退 1/4 屏 |
| Tab 左切 | 完全镜像 |
| 进入子页 | 从右侧整屏飞入，旧页左退 1/4（视差） |
| 返回 | 子页向右退出，下层页从左侧 1/4 处归位 |
| WebView 页 | 只平移不透明，避免 WebView 进 alpha 图层 |

方向判断不靠「是否首次打开」，而是 `targetState.ordinal > initialState.ordinal`（Tab）
和 NavHost 的 `enter/exit/popEnter/popExit` 四组回调（层级），连续切换也不会错向。

## v0.25.0 · Dock 模糊 / 顶栏统一 / WebView 触控 / 转场帧率

### 1. Dock 模糊异常（根因：阴影被压进离屏层）

`PaperDock` 是全项目**唯一**在玻璃 modifier 上挂 `.shadow()` 的：
`GlassSurface` 内部是 `graphicsLayer { compositingStrategy = Offscreen; renderEffect = Blur }`，
阴影跟着进了离屏层 → 阴影丢失、圆角边缘发黑、模糊采样区域被污染。

修复：把 `.shadow(8.dp, shape, clip = false)` 移到外层 `Box`，玻璃只负责模糊。

### 2. 顶部导航栏统一

四个页面本来都调 `GlassSurface`，但参数不一致：首页 `tint = p.card` + `hairlineColor`，
其余三个 `tint = cs.surfaceContainer` 且无描边。现在统一为
`LocalPaper.current.card` + `LocalPaper.current.glassLine`，半径 / 形状 / 圆角同源。

### 3. WebView 触控（根因：丢了去遮罩脚本）

上一轮重写 `WebScreen` 时，把旧 `WebActivity` 里的 `FIX_TOUCH_JS` 弄丢了。
学习通 H5 在非官方容器里会罩一层全屏 `fixed` 引导层（"请在 App 中打开"），
点击全部落在它上面 —— 表现就是「WebView 点不动」。

修复：重新注入 `DEFUSE_OVERLAY_JS`，只把满足「fixed/absolute + 铺满屏幕 +
z-index ≥ 5 + 内部无可交互元素」的节点 `pointer-events: none`，不删节点、不改布局。
`onPageFinished` 立即执行一次，1.2s 后再执行一次（遮罩有时异步插入）。

### 4. 转场帧率（根因：整页 alpha）

页面转场原来同时做 `slide + fadeIn/fadeOut`。整页 alpha 会强制离屏合成，
而每页里还有一层 `GlassSurface`（RenderEffect + 采样层），
一帧要做两次离屏 → 掉帧。

修复：页面转场改为**纯位移**（去掉整页 alpha），保留左右飞入与 1/4 屏视差；
方向判断逻辑不变。WebView 页本来就是纯平移。

### 5. 一言自适应（已有，未改）

`BoardScreen` 里已有 `bigQuote`（`derivedStateOf`）：
剩余 = 视口高 − 列表末项底部 − (78dp + 手势条)，`BigQuote(minHeight = 剩余)` 居中放大；
空间不足时回落到顶栏小字。78dp = Dock 66dp + 底边距 12dp。

## 转场重做 · 单轨道 / 顶栏同高 / Dock 采样修正

### 1. 页面切换：单条水平轨道

`ui/PageSwitcher.kt` 用自定义 `Layout` 实现了一条**轨道**：

```
视口：[ 0 ................. 1080 ]   clipToBounds
轨道：      [ 作业 ][ 课表 ][ 统计 ][ 设置 ]   ← 同一平面、同一 Z、互不覆盖
             ↑ track = -i × w 时第 i 页落在视口里
```

| 项 | 值 |
|---|---|
| 轨迹 | 纯水平位移（只有 `translationX`），无缩放 / 旋转 / 弹跳 / 整页 alpha |
| 曲线 | `cubic-bezier(0.22, 1, 0.36, 1)`（`M3Easing.PageSwitch`） |
| 时长 | 380ms（进、出共用同一个进度，节奏一致） |
| 方向 | 下标变大 = 轨道左移；后退同一个量反着走，没有第二套参数 |
| 中断 | `Animatable<Float>` 从**当前值**续跑，不 `snapTo(0)`、不跳帧 |
| 绘制 | 视口外的页不 `place`（不绘制）；目标页例外，它要留在采样层里 |

四个页面**始终组合**（滚动位置/状态一直保留），切页时不再临时组合，也就没有"首帧组合卡一下"。

### 2. 顶栏同高

`PaperTopBar` 增加固定副标题行 `TopBarSubtitleHeight = 20.dp`，**没有副标题也占位**：

| 页面 | 顶栏内容 |
|---|---|
| 作业 | 标题（全部/作业/考试 ⌄）+ 同步状态行（原来单独一行的 `SyncLine` 收进副标题） |
| 课表 | 课表 + 第 N 周 · N 节 · 共 N 条 |
| 统计 | 统计 + 共 N 项 · N 个科目 |
| 设置 | 设置 + 外观 · 内容 · 账号 · 关于 |

顶栏总高统一为 44 + 20（+ 状态栏），切页时下沿不再一高一低。
一言原来占顶栏第三行（导致作业页更高），现在统一放到列表末尾的 `quote` item：
空间够就放大，不够就小字。

### 3. Dock 玻璃采样：两层，各取所需

问题：Dock 的模糊会**提前采到还没进入视口的下一页**（切「作业 → 设置」时 Dock 里先出现设置页的色块）。

根因：原来只有一张采样层、且只让"目标页"录，Dock 靠 `PageMotion` 把采样点挪回去 ——
一张整页图无论怎么挪，都答不出"接缝左边是哪一页"。

现在 `BackdropState` 有两块 RenderNode：

| 层 | 录什么 | 给谁 |
|---|---|---|
| `pageLayer` | 前台页的完整一帧（内容根，不含顶栏） | 页面内的顶栏玻璃 |
| `trackLayer` | **整条轨道**的完整一帧（窗口坐标） | 页面外的 Dock |

两块层都是窗口坐标，玻璃只做 `translate(-(G-L))` 就地取样，**不需要任何位移补偿**：
接缝扫到哪，Dock 里的内容就跟到哪。

> 为什么必须两块：采样层里不能再画"引用这块采样层"的玻璃，否则 HWUI 会把 RenderNode
> 画进它自己（`RenderNode::prepareTreeImpl` 无限递归 → SIGSEGV，实测必崩）。
> 顶栏玻璃长在页面里，只能取"不含自己"的单页层；Dock 在轨道外，取轨道层才对。

同时删掉：`M3Transitions.kt`（旧的 shared axis 那套）、`PageMotion` / `LocalPageMotion`
（位移补偿）、`M3FixedMotionScale`（强制 1× 时长缩放，和"尊重减少动效"冲突）。

### 4. 减少动效

`rememberPageTransitionSpec()` 读并监听 `Settings.Global.ANIMATOR_DURATION_SCALE`：
为 0（无障碍"移除动画"/开发者选项时长缩放 0）时用 `PageTransition.Reduced` —— 零位移、零时长，直接换页。