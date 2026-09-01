# Nexus Battles VI - UI Kit

This shared UI Kit directory contains the foundational design system assets for the Nexus Battles VI frontend. 
It establishes a single source of truth to ensure a consistent, accessible, and maintainable user interface across all views.

## Contents

- `nexus-tokens.css`: The central registry for all design tokens (colors, typography, sizing) defined in the project's WCAG 2.2 AA light theme.

## Usage

To use the UI kit in your HTML views, import the tokens file in the `<head>` of your document before any view-specific styles:

```html
<link rel="stylesheet" href="../../shared/ui-kit/nexus-tokens.css">
```

Do not redefine global CSS variables in your view-specific CSS. Always reference the tokens from `nexus-tokens.css`.
