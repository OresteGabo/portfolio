# Shared empty states

Use `EmptyState` in `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/EmptyState.kt` for every in-app empty list, first-use state, empty search result and recoverable unavailable state.

It owns the themed gradient, border, orbit illustration, icon tile, typography, spacing and buttons for both Android and iOS. Notifications use this same implementation; there is no separate notification empty-state layout.

## Icon, title and optional description

```kotlin
EmptyState(
    title = "No documents yet",
    icon = Icons.Outlined.Description,
    description = "Your saved documents appear here.",
)
```

Existing screens can use an artwork preset instead:

```kotlin
EmptyState("No active share holdings", EmptyArtwork.SHARES, compact = true)
```

Both forms render through the same implementation. Strings pass through AppText, so add new text to FrenchStrings.kt.

## Optional actions

```kotlin
EmptyState(
    title = "No matches",
    icon = Icons.Outlined.Search,
    action = EmptyStateAction(
        label = "Clear filters",
        onClick = clearFilters,
        icon = Icons.Outlined.FilterAltOff,
    ),
    secondaryAction = EmptyStateAction("View all", viewAll),
)
```

Omit either action to leave it out. `EmptyStateAction.enabled` can disable an action during loading. Icons inside actions are optional. Do not duplicate an existing floating action button just to populate an empty-state action.

## Presentation options

- `compact = true` reduces the illustration and spacing for inline cards, charts and dialogs. It keeps the same visual language.
- `framed = false` omits the extra border inside an existing card.
- `badgeIcon` optionally adds a small decorative status badge, such as Done for a caught-up inbox. Leave it absent for neutral empty states and errors.
- `modifier` controls placement and available width. Content grows with text and font size; avoid fixed-height containers that clip it.

Keep descriptions short and optional. Use the action for the next useful step (clear a filter, retry, navigate), and supply the feature callback at the call site. The shared component must not fetch data or infer behavior from translated button labels. Decorative icons have no accessibility labels; the title is exposed as a heading.
