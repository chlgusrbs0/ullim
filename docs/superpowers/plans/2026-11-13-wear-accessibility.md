# Wear accessibility (#31)

Preserve actual runtime/control behavior while improving semantic and visual access on small circular screens. Base #67; #29 remains another owner's PR, currently separately validated125/126 with one visibility assertion failing. Do not overwrite that branch or claim onboarding/real TalkBack verified. Its integration/accessibility remains pending until that dependency can be integrated with its policy preserved.

Use native Wear controls/roles, state descriptions for capture and current sensitivity/AI selection, screen pane titles and heading order. Do not replace useful visible text with duplicate contentDescription or announce 10Hz graphs as live regions. Make RMS solid and envelope dashed with explicit text/numeric semantics, so color alone never identifies their meaning. Keep common time axis and physical-command disclaimer.

Retain dynamic watch colors only when tested text/button pairs meet4.5:1 and chart marks3:1; otherwise use a verified fallback scheme. Use actual system font scale; no shrinking to hide overflow. All controls have >=48dp touch targets and wrap without ellipsis. Verify2x font on192dp round host through TextLayoutResult, measured button bounds, reachable scroll controls and back. Host screenshots are development evidence only.

- [x] RED pure contrast/fallback tests and Compose semantic/layout tests before feature changes.
- [x] Implement theme contrast selection, semantic state/pane/heading improvements and non-color trace shapes; include main/settings/AI/diagnostic surfaces in review.
- [x] Actual Compose checks for primary start/stop and sensitivity/AI semantics, large-font text/bounds, scrolling/back and status text. Capture/inspect host screenshot if supported.
- [x] Full local tests/build/lint and independent read-only review; fix important findings and prepare main-target Draft Closes #31.
- [ ] Pending device/dependency gates: #29 onboarding integration, real Watch7 TalkBack focus/readout, system navigation, large-font/contrast settings and round-edge screenshots. Do not mark these passed from semantics or host images.
