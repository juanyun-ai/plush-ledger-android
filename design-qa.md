# Design QA

## 0.8.0 全应用重构

### Source Of Truth

- Reference directory: `/Users/leo/Documents/codex/小米模型/绒绒记账UI参考图`
- Compared pages: welcome, home, bills, stats, my, record, budget, category, settings, profile, about.
- Implementation screenshots: `design-qa/screenshots/v080-final-*.png`
- Comparison sheet: `design-qa/screenshots/v080-reference-comparison.png`

### Checks

- Home follows the reference layout: mascot/wordmark header, month chip, monthly summary card, today comparison card, recent records, floating record button, and 4-tab bottom navigation.
- Bills follows the reference layout: mascot title header, month chip, search action, segmented tabs, dual income/expense summary card, grouped bill list, and matching bottom navigation.
- Stats follows the reference layout: compact header, summary card with mascot, donut chart, weekly trend chart, ranking list, and matching bottom navigation.
- My follows the reference layout: wordmark/settings header, warm profile card, metrics panel, menu card, export dialog, and bottom navigation. Extra account features are preserved inside existing menu/detail flows.
- Record follows the reference layout: back/title/search header, segmented type switch, amount card, plush category grid, date/account/note card, and fixed save button.
- Budget, category, settings, profile, and about pages use the newly added reference pages for warm panels, grouped rows, orange primary actions, and mascot/brand embedding.
- Visual style uses warm off-white background, orange primary controls, coffee text, mint/pink/lilac/blue accents, soft rounded cards, plush bitmap assets, and no marketing-style landing content after entry.
- Text does not overflow on the tested Pixel emulator viewport. Android system status/navigation bars are present in implementation screenshots and reduce available height compared with the raw reference PNGs.

Result: passed

## 0.9.1 子分类选择

- Source: `/Users/leo/Documents/codex/小米模型/绒绒记账UI参考图/子分类UI设计图/餐饮.png`
- Implementation screenshot: `app/design-qa-0.9.1-implementation-viewport.png`
- Side-by-side comparison: `app/design-qa-0.9.1-comparison.png`
- Compared state: 支出记账 -> 子分类选择 -> 餐饮，暖黄主题，首个子分类处于选中状态。
- Compared viewport: reference `941 x 1672`; implementation normalized to `941 x 1672` from the Android emulator surface.

## Visual checks

- Header title, back affordance, warm ivory background and dark brown type hierarchy match the source.
- Ten-item left rail uses the supplied 3D category artwork, selected white card, orange marker and orange label.
- Main content uses two-column plush cards, source-derived 3D artwork, warm borders, soft shadow and circular selected badge.
- Root-specific subtitle, healing quote artwork and fixed orange confirmation action are present without text overlap.
- Android status/navigation chrome differs from the iOS chrome in the source; app-owned content remains aligned and complete.

## Interaction checks

- Root categories switch the child grid and automatically select the first available child.
- Child cards update the orange border and check badge.
- Confirm returns to the record screen and persists the selected path, verified with `其他 · 杂项备用`.
- Back returns to the record screen instead of exiting the app.

final result: passed

## 0.9.3 账目保存、AI 与历史图标

### Interaction checks

- 未选择分类时提交账目会显示“请选择具体分类”，金额和备注保持原样。
- AI 识别结果在确认时重新校验当前账本的分类和账户；成功后写入本地账本，失败时不会丢弃识别结果。
- 奶茶、正餐、公交地铁、房租、人情红包、工资、兼职和理财收益均可映射到现有分类。
- 信箱使用云端记录与本机缓存合并；单条版本详情无法获取时，其他历史通知仍可显示。
- 历史“美食、日用消费、人情往来、学习成长、健康医疗、薪资、副业、投资收益、红包礼金”等名称统一显示新版毛绒素材。
- 在没有系统语音服务的测试设备上，语音入口给出服务缺失提示且不崩溃。

final result: passed

## 0.9.2 分类图标与导航稳定性

- Source: `/var/folders/d4/ykzz2mt97fd3zn1spkxltvqw0000gn/T/codex-clipboard-5f7218c8-e683-4a1a-90a5-8ecf895bb360.png`
- Bills screenshot: `app/design-qa-0.9.2-bills.png`
- Subcategory screenshot: `app/design-qa-0.9.2-category.png`
- Income screenshot: `app/design-qa-0.9.2-income.png`
- Side-by-side comparison: `app/design-qa-0.9.2-comparison.png`
- Tested viewport: Android emulator `1080 x 1920`, warm theme, local preview account.

## Visual checks

- 奶茶咖啡、日常、人情社交、学习工作和医疗健康使用了参考图中的对应 3D 毛绒素材。
- 收入分类工资、兼职、理财和礼金的造型、暖色背景与参考图一致。
- 图标使用同一圆角暖白容器，在记录行、分类卡和统计排名中均不裁切、不变形。
- 未知自定义分类回退为毛绒“未分类”素材，不再显示旧式线性图标。

## Interaction checks

- 二级分类卡、一级分类切换、收支切换与确认按钮仍可正常使用。
- 模拟器中快速交替点击“账单”和“我的”九次，最后选择“账单”并等待信箱网络请求返回，页面仍保持在“账单”。

final result: passed
