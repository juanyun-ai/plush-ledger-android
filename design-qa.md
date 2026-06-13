# Design QA - 0.7.5

## Source Of Truth

- Reference directory: `/Users/leo/Documents/codex/小米模型/绒绒记账UI参考图`
- Compared pages: welcome, home, bills, stats, my, record.
- Implementation screenshots: `design-qa/screenshots/v075-final-*.png`
- Comparison sheet: `design-qa/screenshots/v075-reference-comparison.png`

## Checks

- Home follows the reference layout: mascot/wordmark header, month chip, monthly summary card, today comparison card, recent records, floating record button, and 4-tab bottom navigation.
- Bills follows the reference layout: mascot title header, month chip, segmented tabs, summary card, grouped bill list, and matching bottom navigation.
- Stats follows the reference layout: compact header, summary card with mascot, donut/bar chart cards, ranking list, and matching bottom navigation.
- My follows the reference layout: wordmark/settings header, profile card, metrics panel, menu card, and bottom navigation. Extra account features are preserved inside existing menu/detail flows.
- Record follows the reference layout: back/title/search header, segmented type switch, amount card, plush category grid, date/account/note card, and fixed save button.
- Visual style uses warm off-white background, orange primary controls, coffee text, mint/pink/lilac/blue accents, soft rounded cards, plush bitmap assets, and no marketing-style landing content after entry.
- Text does not overflow on the tested Pixel emulator viewport. Android system status/navigation bars are present in implementation screenshots and reduce available height compared with the raw reference PNGs.

## Result

passed
