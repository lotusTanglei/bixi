# Bixi Favicon Exploration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate six non-destructive Bixi favicon drafts across the approved turtle-shell and gemstone routes, then present them for selection.

**Architecture:** Use the built-in ImageGen tool once per distinct concept, producing three shell-based drafts and three gemstone-based drafts. Keep all drafts as preview assets outside the tracked UI favicon paths until the user selects one; validate each draft visually at favicon scale before presenting it.

**Tech Stack:** Built-in ImageGen, local image inspection, existing Vue/Vite favicon reference at `bixi-ui/index.html`.

## Global Constraints

- No text, wordmark, facial details, or illustrative clutter.
- Use a bold silhouette and one memorable negative-space gesture.
- Limit each mark to two or three flat colors with strong contrast.
- Keep the mark legible on both light and dark browser chrome.
- Square 1:1 composition with safe padding.
- No gradients, shadows, texture, glow, watermark, or background scene.
- Avoid thin interior lines that disappear below 32 pixels.
- Do not replace existing favicon files until a final mark is approved.

### Task 1: Generate the three abstract turtle-shell drafts

**Files:**
- Preview-only outputs: ImageGen default generated-image locations; do not copy into project favicon paths.

- [ ] **Step 1: Generate 1A — Shell B**

Create a square favicon concept with a compact six-segment shell silhouette and a geometric negative-space B gesture through the center.

- [ ] **Step 2: Generate 1B — Fortified shell**

Create a square favicon concept combining a rounded shield and turtle-shell structure, with minimal shell divisions and a stable platform feel.

- [ ] **Step 3: Generate 1C — Shell facet**

Create a square favicon concept made from a few angular shell facets, suggesting a turtle back without depicting a complete turtle.

### Task 2: Generate the three abstract gemstone drafts

**Files:**
- Preview-only outputs: ImageGen default generated-image locations; do not copy into project favicon paths.

- [ ] **Step 1: Generate 2A — Faceted B gem**

Create a compact tourmaline crystal silhouette with a central geometric B cutout.

- [ ] **Step 2: Generate 2B — Diamond/V cut**

Create a rhombus gemstone with a strong V-shaped internal cut, preserving a simplified visual echo of the current Bixi mark.

- [ ] **Step 3: Generate 2C — Gem seal**

Create a blue-green geometric gemstone seal with a restrained coral highlight and one dominant silhouette.

### Task 3: Inspect and present the exploration set

**Files:**
- No existing project files modified.

- [ ] **Step 1: Inspect every generated image**

Check silhouette, contrast, absence of accidental text or decorative detail, and recognition at 16/32/64-pixel reductions.

- [ ] **Step 2: Present all six drafts with stable labels**

Show the drafts grouped as 1A–1C and 2A–2C, summarize the strongest and weakest visual properties, and wait for the user to select a direction.

- [ ] **Step 3: Keep all existing favicon assets unchanged**

Do not modify `bixi-ui/public/favicon.ico`, `bixi-ui/public/favicon.png`, or the existing source assets during exploration.
