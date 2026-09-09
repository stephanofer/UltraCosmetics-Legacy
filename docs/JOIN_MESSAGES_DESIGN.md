# Join Messages: Product and Technical Design

## Status

Revised proposed design for team review. Minecraft 1.8.8 support and reusable multi-line chat formatting are established requirements. No product implementation has started.

## Product Goal

Join Messages let a player equip a distinctive server-arrival announcement. When that player joins, eligible online players see the selected message and, when configured for that cosmetic, hear its sound.

The category is intended to be desirable and commercially useful without affecting gameplay. It must remain polished, predictable, safe around vanished players, and inexpensive to execute even on a populated server.

## Product Decisions

| Topic | Decision |
|---|---|
| Public name | Join Messages |
| Spanish localization | Mensajes de entrada |
| Internal category | `JOIN_MESSAGES` |
| Configuration path | `Join-Messages` |
| Permission prefix | `ultracosmetics.joinmessages` |
| Selection model | Exactly zero or one equipped Join Message per player |
| Join behavior | Broadcast only the joining player's equipped message |
| Quit behavior | Never broadcast a cosmetic quit message |
| Vanilla messages | UltraCosmetics takes ownership of join and quit messages by default and suppresses both |
| Supported server | Minecraft 1.8.8 only |
| Message format | Multi-line MiniMessage templates constrained to Minecraft 1.8 formatting |
| Chat directives | Reusable `{BLANK}`, `{CTR}`, and `{NL}` syntax |
| Sound | Optional per cosmetic, heard locally by every eligible recipient |
| Preview | Right-click, private, does not equip, available for locked cosmetics |
| Persistence | Existing profile, flat-file, and MySQL equipped-cosmetic mechanisms |
| Runtime | One central coordinator; no per-player listener and no repeating task |
| Compatibility | Minecraft 1.8.8 Bukkit/Adventure/XSeries only; no later-version support, NMS, or packet dependency |

## Scope

- Add Join Messages as a complete UltraCosmetics category.
- Suppress the standard join and quit chat messages by default.
- Broadcast a selected custom message when a player joins.
- Support manual line breaks, blank lines, per-line centering, and automatic pixel-width wrapping.
- Build the chat-template formatter as an explicit reusable utility for other plugin messages.
- Support an optional sound per Join Message.
- Add a category menu with equip, unequip, purchase, locked-item preview, filtering, and pagination behavior consistent with UltraCosmetics.
- Persist the selected Join Message through the existing profile system.
- Integrate permissions, direct GUI purchase, treasure chests, commands, placeholders, custom menus, and localization.
- Protect vanished players from being revealed by the announcement.
- Provide a data-oriented catalog so a new message does not require a new runtime class.

## Out of Scope

- Quit-message cosmetics.
- Player-authored or freely editable announcement text.
- Hover events, click events, commands, URLs, or arbitrary MiniMessage supplied by players.
- Particles, titles, boss bars, fake entities, PacketEvents, or NMS.
- Proxy-wide broadcasting across multiple backend servers.
- Scheduling, queuing, or replaying a message after the player leaves.
- Compatibility behavior, fallbacks, or testing for Minecraft versions other than 1.8.8.

These can be reconsidered later, but adding them now would dilute a focused product and introduce moderation, spam, security, or infrastructure costs without proving value first.

## User Experience

### Main Menu

The main menu exposes one new category:

```text
Join Messages
Announce your arrival in style.

Unlocked: 3/8
```

Recommended category icon: `OAK_DOOR`, resolved by XMaterial to its Minecraft 1.8 representation.

### Category Menu

Each item communicates the outcome before asking the player to buy or equip it:

```text
Equip: Server

Announces your arrival to the server.

Preview:

Vendimia joined the server.

Left-click to equip
Right-click to preview
```

For the selected item, the normal glow and `Unequip` state remain visible. Equipping a Join Message does not broadcast it immediately; it becomes active on the player's next qualifying login. The immediate feedback is the private preview.

### Interaction Rules

| Input | Owned cosmetic | Locked cosmetic |
|---|---|---|
| Left click | Equip or unequip | Open the existing purchase flow when purchasing is enabled; otherwise show no-permission feedback |
| Right click | Preview privately | Preview privately |

Previewing a locked item is intentional. It lets a player understand exactly what they are considering before spending money, which is better product behavior than hiding the primary value behind purchase.

### Preview Behavior

- Send every rendered announcement line only to the requesting player.
- Play the optional sound only to the requesting player.
- Never equip, unlock, purchase, persist, or broadcast anything.
- Keep the menu open so messages can be compared quickly.
- Apply a short per-player cooldown, defaulting to 2 seconds, to prevent sound and chat spam.
- Do not require ownership, but do require the category to be enabled and the cosmetic itself to be enabled.
- Render `<player>` with the previewing player's real account name.

The dedicated Join Message button must be chosen before the generic no-permission replacement item. Otherwise the existing `CosmeticNoPermissionButton` would hide the identity and preview of locked cosmetics.

## Live Join Flow

```text
PlayerJoinEvent
      |
      +--> suppress current Bukkit join message
      |
      +--> create/load UltraPlayer profile asynchronously (existing behavior)
      |
      v
main-thread profile-loaded callback
      |
      +--> verify player is still online
      +--> restore equipped cosmetics
      +--> resolve equipped JOIN_MESSAGES selection
      +--> validate category, type, world, permission, and visibility policy
      +--> render and pixel-wrap all message lines once
      +--> capture eligible online recipients
      +--> send the same bounded component list to every recipient
      +--> optionally play one local sound per recipient
```

The announcement must happen after profile loading, not directly in `PlayerJoinEvent`. The current profile is loaded asynchronously, so the selected cosmetic is not guaranteed to exist during the original event. Triggering from the event would create a race that works with fast file storage and intermittently fails with MySQL.

The profile callback and announcement execute on the main thread. No Bukkit player, world, visibility, audience, or sound API is called asynchronously.

### Exactly-Once Rule

One physical login can produce at most one Join Message. Profile re-equipping after respawn or world change must never announce again. The trigger belongs only to the profile-loaded join flow, not to `JoinMessage.onEquip()`.

### Delayed Profile Loads

A slow database may delay the custom announcement because correctness requires knowing the persisted selection. The coordinator records the original join time and skips the announcement after a configurable maximum age, defaulting to 5 seconds. A stale arrival announcement is worse than a missed one.

If the player disconnects before loading completes, nothing is sent.

## Vanilla Message Ownership

Bukkit exposes only the event's current join or quit message. It does not identify whether that value came from vanilla or from another plugin. Comparing text against an assumed vanilla sentence would be locale-dependent and fragile.

The correct model is explicit ownership:

```yaml
Join-Message-Settings:
  Take-Over-Join-Message: true
  Suppress-Quit-Message: true
```

With `Take-Over-Join-Message: true`, UltraCosmetics sets the current join message to `null` at `HIGHEST` priority. This suppresses vanilla and changes made by lower-priority listeners. A plugin incorrectly modifying the event at `MONITOR` can still replace it; that is an integration conflict and must be documented rather than countered by violating Bukkit's event-priority contract.

Suppression is independent of category enablement. With takeover enabled and no selected cosmetic, joining is intentionally silent. This is the required default baseline.

On quit, UltraCosmetics suppresses the current quit message and sends no replacement. Cleanup and profile saving continue through the existing quit lifecycle.

## Audience Policy

The announcement is server-wide across Bukkit worlds. A recipient is eligible when:

- The recipient is online when the profile-loaded callback runs.
- The joining player is still online.
- The recipient is the joining player or `recipient.canSee(joiningPlayer)` returns true.

The `canSee` check is mandatory. A global cosmetic must NEVER reveal a vanished staff member to players who cannot see them.

The joining player receives their own announcement. This confirms that the cosmetic worked and makes preview and live output consistent.

The initial release does not add a viewer opt-out. The product requirement is a server announcement visible to everyone who is allowed to know that the player joined. If player-level muting becomes necessary later, chat and sound preferences should be separate so accessibility does not erase the cosmetic's social value.

## Message Rendering

Each cosmetic owns one message template stored in the message resource so it participates in the existing localization and MiniMessage systems. A template may be a YAML list, which is preferred for readability, or a scalar containing `{NL}` separators.

Preferred form:

```yaml
Join-Messages:
  Server:
    name: <bold><yellow>Server
    Description: Announces your arrival to the server.
    Message:
      - "{BLANK}"
      - "{CTR}<bold><yellow><player> JOINED THE SERVER"
      - "{BLANK}"
```

Equivalent compact form:

```yaml
Message: "{BLANK}{NL}{CTR}<bold><yellow><player> JOINED THE SERVER{NL}{BLANK}"
```

Both forms produce exactly three physical chat lines: one blank line, one centered announcement, and one blank line.

Approved placeholders for the first release:

| Placeholder | Value |
|---|---|
| `<player>` | Joining player's account name, inserted as unparsed text |
| `<world>` | Joining world name, inserted as unparsed text |
| `<online>` | Online player count at send time |
| `<max_players>` | Configured server capacity |

`<player>` must use `Placeholder.unparsed`, not parsed MiniMessage, even though Minecraft account names are constrained. This keeps the trust boundary correct and prevents future data sources from introducing markup injection.

Templates are controlled by server configuration, not players. Every template is compiled and validated once while the catalog loads. Runtime placeholder values are resolved before measuring or wrapping because values such as `Vendimia` and `iiiiiiiii` have different pixel widths.

The template is rendered once per join into a bounded immutable `List<Component>`, and the same resulting lines are reused for all recipients. It is not parsed or wrapped once per viewer.

### Minecraft 1.8 Formatting Boundary

MiniMessage remains the server-side authoring parser because UltraCosmetics already uses Adventure and MiniMessage. It does not require a modern Minecraft client. The resulting components must, however, use only presentation that Minecraft 1.8 can represent reliably:

- The 16 named legacy colors.
- Bold, italic, underlined, strikethrough, and obfuscated decorations.
- No RGB or hexadecimal colors.
- No custom font tags.
- No hover, click, insertion, URL, command, or other interactive events.
- No gradients or rainbows in Join Message templates.

The formatter should reject unsupported tags for Join Message templates instead of accepting content that silently changes appearance on the target client.

### Directive Grammar

| Directive | Scope | Behavior |
|---|---|---|
| `{BLANK}` | Entire logical line | Emits one empty physical chat line |
| `{CTR}` | Start of a logical line | Centers that logical line and every wrapped fragment it produces |
| `{NL}` | Scalar template | Ends the current logical line and starts another |

Rules:

- Directives are case-insensitive.
- `{CTR}` is evaluated independently for every logical line. It is not a global switch.
- `{BLANK}` is valid only when it is the complete trimmed logical line.
- `{NL}` is processed before MiniMessage parsing and preserves empty segments; `{NL}{NL}` therefore inserts one blank line.
- An empty entry in a YAML list also represents one blank line.
- Unknown brace tokens remain text unless a future formatter version explicitly registers them.
- Directive parsing is deterministic and does not use regular-expression replacement after MiniMessage rendering.

For example:

```yaml
Message: "{CTR}<red><bold>NEW GAME{NL}<gray>Answer the following question"
```

Only the first line is centered. Centering both lines requires a second `{CTR}`:

```yaml
Message: "{CTR}<red><bold>NEW GAME{NL}{CTR}<gray>Answer the following question"
```

The same formatter can reproduce the supplied chat-game presentation without becoming coupled to Join Messages:

```yaml
trivia-start:
  - "{CTR}<red><bold>NEW GAME"
  - "{BLANK}"
  - "{CTR}<gray>The first player to complete <dark_gray>'<white><game_content><dark_gray>' <gray>wins!"
  - "{CTR}<gray><italic>(Type only the missing letters)"
```

Legacy `&c&l` examples describe the same Minecraft 1.8 colors and decorations, but new templates use the repository's existing MiniMessage authoring format. Supporting two editable syntaxes in the same template would make validation and escaping ambiguous. Legacy components remain an internal serialization option for width measurement, not a second public template language.

### Reusable Chat Formatting Architecture

The directives and pixel layout are not implemented inside `JoinMessageCoordinator`. They form an explicit opt-in chat formatting facility:

```text
ChatMessageTemplate
        |
        v
ChatMessageFormatter
        |
        +--> Minecraft18FontMetrics
        +--> PixelLineWrapper
        |
        v
List<Component>
```

| Component | Responsibility |
|---|---|
| `ChatMessageTemplate` | Immutable compiled logical lines and per-line directives |
| `ChatMessageFormatter` | Resolve placeholders, render 1.8-compatible MiniMessage, wrap, center, and return final components |
| `Minecraft18FontMetrics` | Measure vanilla Minecraft 1.8 glyph advances while tracking inherited bold state |
| `PixelLineWrapper` | Wrap words and overlong tokens by pixel width while preserving component styles |
| `FormattedChatLine` | Immutable logical line content plus centering and blank-line state |

Conceptual API:

```java
public interface ChatMessageFormatter {

    List<Component> format(
            ChatMessageTemplate template,
            TagResolver... placeholders
    );
}
```

Join Messages, chat games, alerts, rewards, and minigame instructions may invoke this utility explicitly. It must not silently replace all `MessageManager` behavior, because existing plugin messages do not opt into directives, wrapping, or multi-line limits.

### Formatting Pipeline

```text
YAML scalar or list
        |
        v
split logical lines and compile directives once
        |
        v
resolve safe runtime placeholders
        |
        v
render 1.8-compatible MiniMessage components
        |
        v
wrap each logical line by measured pixel width
        |
        v
preserve colors and decorations across wrapped fragments
        |
        v
prepend calculated spaces to centered fragments
        |
        v
enforce final physical-line limit
        |
        v
immutable List<Component>
```

Wrapping by an approximate character count is forbidden. `iiiiiiiiii` and `WWWWWWWWWW` contain the same number of characters but occupy materially different widths.

### Font Metrics Reference

Minecraft uses a proportional font. The following standard `DefaultFontInfo` table is the implementation reference for the default ASCII glyphs. The production lookup must be indexed after class initialization so measuring a character is `O(1)`; it must not scan `values()` for every character.

The supplied KixsChatGames-style reference is adopted selectively:

| Supplied behavior | Decision |
|---|---|
| `CENTER_PX = 154` | Retained as the vanilla Minecraft 1.8 center target |
| Space advance of four pixels | Retained: three-pixel glyph plus one-pixel spacing |
| Bold adds one pixel to non-space glyphs | Retained |
| `DefaultFontInfo` ASCII widths | Retained as the baseline table and reproduced below |
| Linear `values()` search per character | Replaced with an indexed lookup |
| Wrap after 38 visible characters | Replaced with measured pixel-width wrapping |
| Any formatting code disables bold | Corrected to follow Minecraft legacy formatting state |
| One leading `{CTR}` controls every line | Replaced with independent per-line directives |
| Hex-color recognition | Removed from the Minecraft 1.8 contract |

```java
public enum DefaultFontInfo {
    A('A', 5), a('a', 5), B('B', 5), b('b', 5), C('C', 5), c('c', 5), D('D', 5), d('d', 5),
    E('E', 5), e('e', 5), F('F', 5), f('f', 4), G('G', 5), g('g', 5), H('H', 5), h('h', 5),
    I('I', 3), i('i', 1), J('J', 5), j('j', 5), K('K', 5), k('k', 4), L('L', 5), l('l', 1),
    M('M', 5), m('m', 5), N('N', 5), n('n', 5), O('O', 5), o('o', 5), P('P', 5), p('p', 5),
    Q('Q', 5), q('q', 5), R('R', 5), r('r', 5), S('S', 5), s('s', 5), T('T', 5), t('t', 4),
    U('U', 5), u('u', 5), V('V', 5), v('v', 5), W('W', 5), w('w', 5), X('X', 5), x('x', 5),
    Y('Y', 5), y('y', 5), Z('Z', 5), z('z', 5),
    NUM_1('1', 5), NUM_2('2', 5), NUM_3('3', 5), NUM_4('4', 5), NUM_5('5', 5),
    NUM_6('6', 5), NUM_7('7', 5), NUM_8('8', 5), NUM_9('9', 5), NUM_0('0', 5),
    EXCLAMATION('!', 1), AT_SYMBOL('@', 6), NUM_SIGN('#', 5), DOLLAR_SIGN('$', 5),
    PERCENT('%', 5), CIRCUMFLEX('^', 5), AMPERSAND('&', 5), ASTERISK('*', 5),
    LEFT_PARENTHESIS('(', 4), RIGHT_PARENTHESIS(')', 4), MINUS('-', 5), UNDERSCORE('_', 5),
    PLUS_SIGN('+', 5), EQUALS_SIGN('=', 5), LEFT_CURL_BRACE('{', 4), RIGHT_CURL_BRACE('}', 4),
    LEFT_BRACKET('[', 3), RIGHT_BRACKET(']', 3), COLON(':', 1), SEMI_COLON(';', 1),
    DOUBLE_QUOTE('"', 3), SINGLE_QUOTE('\'', 1), LEFT_ARROW('<', 4), RIGHT_ARROW('>', 4),
    QUESTION_MARK('?', 5), SLASH('/', 5), BACK_SLASH('\\', 5), LINE('|', 1),
    TILDE('~', 5), TICK('`', 2), PERIOD('.', 1), COMMA(',', 1), SPACE(' ', 3),
    DEFAULT('a', 4);

    private static final DefaultFontInfo[] ASCII_LOOKUP = new DefaultFontInfo[128];

    static {
        java.util.Arrays.fill(ASCII_LOOKUP, DEFAULT);
        for (DefaultFontInfo glyph : values()) {
            if (glyph != DEFAULT && glyph.character < ASCII_LOOKUP.length) {
                ASCII_LOOKUP[glyph.character] = glyph;
            }
        }
    }

    private final char character;
    private final int length;

    DefaultFontInfo(char character, int length) {
        this.character = character;
        this.length = length;
    }

    public char getCharacter() {
        return character;
    }

    public int getLength() {
        return length;
    }

    public int getBoldLength() {
        return this == SPACE ? length : length + 1;
    }

    public static DefaultFontInfo getDefaultFontInfo(char character) {
        return character < ASCII_LOOKUP.length ? ASCII_LOOKUP[character] : DEFAULT;
    }
}
```

The table alone is not sufficient for localized production messages. `Minecraft18FontMetrics` must additionally define verified widths for at least Latin-1 characters used by maintained translations, including accented Spanish characters such as `á`, `é`, `í`, `ó`, `ú`, `ñ`, `¿`, and `¡`. Unsupported glyphs use one documented fallback advance and produce a load-time warning when encountered in a built-in catalog template.

### Width and Centering Reference

The following code demonstrates the required legacy-width state machine after a component has been serialized to Minecraft 1.8 legacy formatting. It corrects a common bug where italic, underline, or another decoration incorrectly disables bold.

```java
public final class ChatUtil {
    private static final char COLOR_CHAR = '\u00A7';
    private static final int CENTER_PX = 154;
    private static final int SPACE_ADVANCE_PX = DefaultFontInfo.SPACE.getLength() + 1;

    private ChatUtil() {
    }

    public static int pixelWidth(String legacyText) {
        int width = 0;
        boolean bold = false;

        for (int i = 0; i < legacyText.length(); i++) {
            char character = legacyText.charAt(i);
            if (character == COLOR_CHAR && i + 1 < legacyText.length()) {
                char code = Character.toLowerCase(legacyText.charAt(++i));
                if (code == 'l') {
                    bold = true;
                } else if (code == 'r' || isColorCode(code)) {
                    bold = false;
                }
                // k, m, n, and o do not disable an already active bold style.
                continue;
            }

            DefaultFontInfo glyph = DefaultFontInfo.getDefaultFontInfo(character);
            width += (bold ? glyph.getBoldLength() : glyph.getLength()) + 1;
        }

        return width;
    }

    public static String centerLegacy(String legacyText) {
        int leftPaddingPx = Math.max(0, CENTER_PX - pixelWidth(legacyText) / 2);
        int spaces = leftPaddingPx / SPACE_ADVANCE_PX;
        StringBuilder centered = new StringBuilder(legacyText.length() + spaces);
        for (int i = 0; i < spaces; i++) {
            centered.append(' ');
        }
        return centered.append(legacyText).toString();
    }

    private static boolean isColorCode(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }
}
```

This is a reference for the width algorithm, not permission to build the production formatter around repeated legacy string conversions. The preferred implementation walks Adventure components with inherited styles, wraps component segments without losing formatting, and prepends a space component. If component traversal proves disproportionately complex during the vertical slice, one well-contained legacy adapter is acceptable because Minecraft 1.8 is the only target; it must round-trip through the existing serializers and have unit coverage.

### Pixel Word-Wrap

The production wrapper uses pixel width, not the `38-40` visible-character approximation:

```text
for each styled word:
    proposed = current line + one space + word
    if pixelWidth(proposed) <= maximum line width:
        append word
    else:
        emit current line
        begin next line with the active styles and word

if one styled word exceeds the maximum by itself:
    split it at glyph boundaries
    preserve active styles in every fragment
```

Additional rules:

- A centered logical line produces centered wrapped fragments.
- A non-centered logical line produces non-centered wrapped fragments.
- Formatting state carries across automatic wraps but not across independent YAML entries unless the template explicitly repeats it.
- Manual `{NL}` starts a new formatting scope after the scalar has been divided into logical lines.
- Blank lines bypass measurement and wrapping.
- The line cap is enforced after wrapping, because one configured line may produce several physical lines.

### Client-Side Limitation

The server cannot read each Minecraft 1.8 client's chat-width slider, GUI scale, or resource-pack font metrics. Pixel centering is therefore guaranteed only against the vanilla 1.8 font metrics and the documented default/full chat-width target. It is best-effort for clients that alter those settings. No packet or server-side algorithm can eliminate that limitation without client cooperation.

## Sound Design

Sound is an optional immutable property of `JoinMessageType`:

```yaml
Join-Messages:
  Royal:
    Enabled: true
    Show-Description: true
    Treasure-Chest-Weight: 1
    Purchase-Price: 1500
    Sound:
      Name: ENTITY_PLAYER_LEVELUP
      Volume: 0.8
      Pitch: 1.0
```

Rules:

- Omit `Sound` or leave `Name` empty for a silent cosmetic.
- Resolve names through XSound once during catalog loading, not on every join.
- Clamp volume to `0.0-2.0` and pitch to `0.5-2.0`.
- An invalid or unsupported sound logs one startup warning and disables only that sound, not the cosmetic.
- Play at each recipient's own location so the sound is clearly audible and works across worlds.
- Use short UI or signature sounds. Avoid long, aggressive, or repeated audio.
- A global `Sounds` setting can disable all live and preview sounds without disabling messages.

Playing at the joining player's location would make cross-world delivery impossible and turn a global announcement into positional audio. Recipient-local playback matches the product requirement.

## Domain Model

### Category

Add `Category.JOIN_MESSAGES` with:

```text
config path: Join-Messages
chat placeholder: join-message-name
permission path: joinmessages
command prefix: jm
clear on death: false
```

`clear on death` is false because this is a persistent account selection, not an active world object.

### JoinMessageType

`JoinMessageType` extends `CosmeticType<JoinMessage>` and contains only immutable catalog data:

- Stable config name.
- Menu material.
- Localized name and description through existing mechanisms.
- Message resource path/template.
- Optional pre-resolved sound specification.

Every catalog entry uses the same type and cosmetic classes. Adding `JoinMessageRoyal`, `JoinMessageEnder`, and similar empty subclasses would create files without behavior and make the catalog expensive to maintain.

### JoinMessage

`JoinMessage` is a passive `Cosmetic<JoinMessageType>` selection:

- `onEquip()` performs no broadcast.
- It does not register join or quit handlers.
- It does not schedule a task.
- It owns no recipient collection or mutable execution state.

### JoinMessageCoordinator

One coordinator owns live announcements and previews:

- Resolve and validate the selected type.
- Invoke the shared formatter to produce the final bounded lines.
- Build the eligible audience.
- Send chat and sound.
- Track preview cooldowns.
- Remove cooldown entries when they expire or the player quits.

The coordinator must not own profile persistence; that remains in the existing UltraPlayer/Profile system.

### Chat Formatting Services

`ChatMessageTemplate`, `ChatMessageFormatter`, `Minecraft18FontMetrics`, and `PixelLineWrapper` belong in a generic chat-formatting package rather than the Join Messages package. They contain no Bukkit player lookup, cosmetic selection, broadcast policy, sound behavior, or persistence logic. This boundary keeps them reusable without coupling unrelated messages to cosmetics.

## Catalog Strategy

Start with a small, differentiated catalog and validate desirability before producing dozens of near-identical sentences.

| Internal name | Message direction | Sound direction | Suggested tier |
|---|---|---|---|
| `Server` | `<player> joined the server.` | None | Common/default-style |
| `Royal` | `The realm welcomes <player>.` | Short level-up cue | Rare |
| `Ender` | `<player> emerged from the void.` | Enderman teleport | Rare |
| `Lightning` | `<player> struck the server like lightning.` | Restrained thunder | Epic |
| `Firework` | `<player> joined with a bang!` | Firework blast | Epic |
| `Dragon` | `A legendary presence arrived: <player>.` | Short dragon cue | Legendary |
| `Mystery` | `Something changed... <player> is here.` | None | Rare |
| `Arcade` | `PLAYER 1 READY: <player>` | Experience pickup | Epic |

Final wording, colors, icons, prices, and weights are product-content decisions and should be reviewed in game. Tier value should come from identity, typography, and restrained audio, not from making expensive items louder or more disruptive.

No Join Message is granted merely by having the category-open permission. Ownership continues to come from permissions, unlock data, direct purchase, or treasure rewards, consistent with existing cosmetics.

## Configuration

Shared behavior stays separate from individual cosmetic definitions:

```yaml
Categories-Enabled:
  Join-Messages: true

Categories:
  Join-Messages:
    Main-Menu-Item: OAK_DOOR
    Go-Back-Arrow: true

Join-Message-Settings:
  Take-Over-Join-Message: true
  Suppress-Quit-Message: true
  Sounds: true
  Preview-Cooldown-Seconds: 2
  Maximum-Announcement-Delay-Seconds: 5

Chat-Formatting:
  Center-Pixel: 154
  Maximum-Line-Width-Pixels: 300
  Maximum-Output-Lines: 5

TreasureChests:
  Loots:
    Join-Messages:
      Enabled: true
      Chance: 5
      Message:
        enabled: false
```

Per-cosmetic defaults remain under `Join-Messages.<Type>` using the established `Enabled`, `Show-Description`, `Treasure-Chest-Weight`, and `Purchase-Price` keys plus the optional `Sound` section.

Numeric settings are validated once at load time. Invalid values are clamped with one concise warning rather than checked repeatedly on the join path.

`Center-Pixel` and `Maximum-Line-Width-Pixels` describe the vanilla Minecraft 1.8 chat target, not client-specific settings. `Maximum-Output-Lines` is enforced after automatic wrapping, and `{BLANK}` counts as one output line. Five lines allow the intended blank-title-blank presentation while preventing one cosmetic from clearing a recipient's chat history.

## Integration Surface

The category must be added consistently to:

- `Category` registration and category enablement.
- `CosmeticType.registerAll()`.
- `Menus` and the main menu layout.
- Generic toggle/clear behavior.
- Commands and tab completion derived from `Category`.
- Permission registration and `ultracosmetics.allcosmetics`.
- Flat-file and MySQL equipped selection storage.
- Unlocked cosmetic storage.
- Treasure chest category selection and loot messages.
- Purchase and discount flow.
- PlaceholderAPI current-cosmetic lookup through the category prefix.
- Default configuration.
- English and maintained localization resources.
- Generic chat-template parsing, Minecraft 1.8 metrics, pixel wrapping, and centering utilities.

Adding a new category raises the visible main-menu count above the current hard-coded maximum of ten. `MenuMain.makeLayout()` must be changed to support the new count or replaced with a bounded algorithm. Without this, a server enabling every category can receive a null layout and fail while opening the menu.

The category prefix must be unambiguous. `jm` is preferred over `j` so future categories do not collide through the current `startsWith` parser.

## Persistence and Compatibility

This is a new category, so no migration of old selections or permissions is required.

Existing persistence can store it automatically once registered:

- Flat file: `enabled.join_messages: server`
- Unlocked list: `JOIN_MESSAGES:Server`
- MySQL category: `join_messages`

The internal enum and cosmetic config names become persisted identifiers. They must be treated as stable after release. Public display names may change through localization without changing those identifiers.

No additional database table or profile field is justified.

## Performance Model

The join path is intentionally bounded:

```text
CPU: O(number of online recipients)
Formatting: once per join, bounded by template characters and five output lines
Chat sends: at most five per eligible recipient
Sound sends: zero or one per eligible recipient
Scheduling: no repeating task
World/entity work: none
Persistent reads: reuse the existing asynchronous profile load
Persistent writes: none during announcement
```

An O(n) recipient pass is the minimum correct cost for sending a personalized server-wide client message. Hiding that loop behind `Bukkit.broadcast` would not make the network cost disappear and would prevent visibility filtering.

Required implementation details:

- Compile static directives once when loading the catalog.
- Resolve placeholders, render, wrap, and center once per join, then reuse the immutable component list.
- Use indexed glyph metrics rather than scanning an enum for every character.
- Resolve sound configuration once at startup.
- Capture `Bukkit.getOnlinePlayers()` and evaluate recipients only on the main thread.
- Do not create a scheduler per player or per cosmetic.
- Do not retain recipient lists after delivery.
- Use UUID-keyed cooldown timestamps only for recent previews.
- Remove expired preview cooldown entries opportunistically; no cleanup ticker is needed.
- Do not invoke PlaceholderAPI per recipient if support is added later; expansion must happen once for the joining player.

This feature does not need a packet budget, scene manager, object pool, async broadcaster, or caching layer. Those tools would optimize work that does not exist while increasing failure modes.

## Failure and Conflict Policy

- Profile load failure or excessive delay: keep join silent; never restore the vanilla message late.
- Player disconnects before callback: skip.
- Category or selected type disabled: skip.
- Selected permission removed: do not announce; normal equip validation remains authoritative.
- Invalid message template: disable that type at load and report the exact path.
- Unsupported formatting tag or excessive configured output: disable that type at load and report the exact path.
- Runtime placeholder expansion causes more than five wrapped lines: omit the announcement and log through a rate-limited diagnostic path; never send a truncated premium message with a changed meaning.
- Invalid sound: keep the text cosmetic and disable only its sound.
- No eligible recipients: return without further work.
- Vanished joining player: only recipients who can see that player receive the message and sound.
- Another plugin owns join formatting: set `Take-Over-Join-Message` to false and accept that UltraCosmetics will no longer suppress its output. Running two independent join broadcasters is an administrator configuration error, not something to solve with event-priority escalation.

## Public API Event

Publish a cancellable event immediately before live delivery:

```text
UCJoinMessageEvent
```

It exposes the joining `UltraPlayer`, selected `JoinMessageType`, a mutable `List<Component>` of rendered physical lines, and a mutable copy of proposed recipients. It is not fired for private previews.

This lets moderation, minigame, staff-mode, or lobby plugins cancel or narrow an announcement without duplicating UltraCosmetics internals. The event runs synchronously. The coordinator makes a defensive audience copy after the event before delivery.

The first release should not expose a public `broadcast(player)` method. A replay API would make duplicate and command-triggered spam easy. Add one only when a concrete integration requires it.

## Testing Strategy

### Automated Tests

Use the repository's approved JUnit version for isolated logic only:

- Scalar and YAML-list templates compile into the same logical-line model.
- `{BLANK}`, `{CTR}`, and `{NL}` follow their documented scopes, including repeated and empty lines.
- Unknown directives remain literal text.
- Unsupported Minecraft 1.8 formatting is rejected.
- Placeholder rendering inserts player/world/count values as plain text.
- Pixel measurement covers narrow, wide, bold, reset, color-change, decorated, and Latin-1 text.
- Italic, underline, strikethrough, and obfuscated codes do not incorrectly disable bold.
- Pixel wrapping uses measured width, preserves styles, and splits one overlong token at glyph boundaries.
- Centered wrapped lines center every fragment independently.
- Non-centered lines receive no padding.
- Final physical-line limits include blank and automatically wrapped lines.
- Sound volume and pitch clamping.
- Announcement maximum-age boundary.
- Preview cooldown boundary and replacement behavior.
- Recipient policy includes self and excludes viewers who cannot see the joining player, if the policy is extracted behind a server-independent predicate.

Do not add a Minecraft mocking framework solely for this feature. Event dispatch, Adventure delivery, Bukkit visibility, and sound rendering are better verified on the real server.

### Manual Validation

- Player with no selection joins: no join message and no sound.
- Player with a silent single-line selection joins: exactly one custom line reaches every eligible player.
- Three-line blank-centered-blank template renders exactly three lines in the intended order.
- `{NL}` scalar and YAML-list forms produce the same visible output.
- Mixed centered and non-centered lines preserve their independent alignment.
- Long messages wrap at the configured pixel width rather than at a character count.
- Bold and colored text remains visually centered on an unmodified Minecraft 1.8 client.
- Accented maintained-localization text uses verified metrics rather than the ASCII fallback.
- Player with a sound selection joins: all message lines and exactly one sound reach every eligible player.
- Joining player receives their own lines and sound.
- No quit line appears for selected or unselected players.
- Locked cosmetics can be previewed but not equipped without purchase or permission.
- Preview reaches only the requester and does not change selection.
- Rapid preview clicks respect cooldown.
- Selection survives reconnect with flat-file profiles.
- Selection survives reconnect with MySQL profiles.
- Slow profile loading beyond the maximum age remains silent.
- Disconnect during profile loading remains silent.
- Vanished staff are not revealed to players who cannot see them.
- Allowed viewers still receive a vanished staff announcement where visibility policy permits it.
- Disabled category, disabled type, removed permission, and disabled world remain silent.
- All categories enabled: the main menu opens and lays out every category without error.
- Direct purchase, treasure unlock, clear command, category clear, and global clear behave normally.
- Invalid sound configuration produces one warning and keeps text working.
- Unsupported tags and templates exceeding safe output limits fail validation clearly.
- Custom chat width or resource-pack fonts are recorded as best-effort client limitations, not server defects.
- Another join plugin is tested with takeover both enabled and disabled.
- Reload does not duplicate listeners or produce duplicate announcements.

## Acceptance Criteria

- Vanilla/current Bukkit join and quit messages are suppressed by default.
- A player without an equipped Join Message enters and leaves silently.
- A qualifying player with a selected Join Message produces exactly one server-wide custom announcement per login.
- No cosmetic message is produced on quit, respawn, or world change.
- Multi-line messages support YAML lists and `{NL}`, and `{BLANK}` emits intentional empty lines.
- `{CTR}` centers each marked line and every wrapped fragment using Minecraft 1.8 pixel metrics.
- Automatic wrapping uses pixel width rather than character count and preserves active styles.
- Message colors and decorations render through a Minecraft 1.8-constrained Adventure/MiniMessage pipeline.
- Unsupported modern formatting does not silently leak into the catalog.
- Preview and live delivery use the same formatter and produce identical lines for the same player context.
- Optional sounds are heard once by the same eligible audience as the text.
- Cross-world recipients hear sounds locally rather than at an invalid foreign-world location.
- Players who cannot see a vanished joining player receive neither text nor sound.
- Right-click preview is private, works for locked items, and never changes ownership or selection.
- Left-click selection, unequip, purchase, permission, clear, persistence, treasure, and filtering behavior remain consistent with other categories.
- The full main menu remains valid when every category is enabled.
- The feature creates no entities, modifies no world state, starts no repeating task, and performs no synchronous database or file access.
- Invalid optional audio cannot disable an otherwise valid message.
- The implementation and manual validation demonstrate one announcement at most for every physical login.

## Development Sequence

1. Implement and test template compilation, Minecraft 1.8 font metrics, pixel wrapping, centering, and formatting limits.
2. Register the category, type catalog, menu, messages, configuration, permissions, treasure integration, and persistence path.
3. Implement the passive selection and dedicated preview button.
4. Implement event suppression and the profile-aware central coordinator.
5. Add the API event and visibility filtering.
6. Validate flat-file, MySQL, vanish, cross-world, other-plugin, reload, and full-menu scenarios on the real server.
7. Review the catalog in game and tune wording, color hierarchy, sound restraint, prices, and treasure weights from actual player experience.

## Review Focus

The team should review these product choices before development begins:

- UltraCosmetics owns and suppresses both join and quit messages by default.
- Announcements are server-wide across worlds rather than limited to the joining world.
- The joining player receives their own announcement.
- Locked cosmetics are previewable.
- Vanish visibility overrides monetization visibility.
- A five-second profile-load age limit prefers silence over a stale announcement.
- Messages support bounded blank lines, manual line breaks, automatic pixel wrapping, and per-line centering.
- Minecraft 1.8.8 is the only supported runtime and formatting target.
- The reusable formatter is explicit opt-in infrastructure, not a global behavior change to `MessageManager`.
- The first release excludes player-authored text, proxy-wide delivery, and visual effects.

Once these are accepted, the technical implementation has no unresolved architectural fork.
