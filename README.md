# Sable Camera Anchor

Camera anchor system for **Sable** sub-levels on **NeoForge 1.21.1**.

Attach invisible camera anchors to assembled Sable ships, set their local angle (pitch / yaw / roll), offset them in ship space, configure per-camera FOV, and view them live while remaining in full control of your character, or spectate and cycle through them in **ReplayMod**.

---

## Disclaimer

The mod has been assisted with **AI Tools** and **Models**, primarily `Google Antigravity`.

---

## Requirements

- **Minecraft** 1.21.1
- **NeoForge** 21.1.x
- **Sable** 2.0.3+
- **Java** 21

---

## Permissions

Commands are split between **everyone** and **operators (level 2)**:

- **All players (permission level 0):**
  - `/camanchor view <tag>`
  - `/camanchor exit`
  - `/camanchor list`
  - Cycling cameras with arrow keys while viewing
- **Operators only (permission level 2):**
  - `/camanchor addcam`, `delcam`, `angle`, `offset`, `fov`, `show`
  - Saving unlocked mouse-aim / FOV adjustments back to the server (`L`)

---

## Server Commands (`/camanchor`)

| Command | Permission | Description |
|---------|------------|-------------|
| `/camanchor addcam <tag> [pos]` | Op (2) | Spawn a camera on the Sable sub-level you are standing on (default FOV `90`) |
| `/camanchor delcam <tag\|all>` | Op (2) | Delete the matching camera(s), or `all` to delete every camera |
| `/camanchor angle <pitch> <yaw> [roll] [fov] <tag\|all>` | Op (2) | Set ship-local look angles (degrees, positive pitch = **up**) and optional FOV (`1`–`170`) |
| `/camanchor offset <x> <y> <z> <tag\|all>` | Op (2) | Set ship-local position offset |
| `/camanchor fov <fov> <tag\|all>` | Op (2) | Set camera FOV (`1`–`170`, independent of Minecraft's FOV slider) |
| `/camanchor view <tag>` | Everyone | Enter non-spectator camera viewing mode |
| `/camanchor exit` | Everyone | Exit camera viewing mode |
| `/camanchor list` | Everyone | List all camera tags in the level |
| `/camanchor show <true\|false>` | Op (2) | Toggle debug visuals (cyan body box + yellow/orange look cone) |

### Placement grid (`addcam <tag> <pos>`)

When `<pos>` is provided, the camera is placed on a 3×3 grid on top of the block beneath your feet (looking north):

```
topleft     topcenter     topright
centerleft  center        centerright
bottomleft  bottomcenter  bottomright
```

Omit `<pos>` to place the camera at your exact standing coordinates. You **must** be standing on an assembled Sable sub-level when running `addcam`.

---

## Client & ReplayMod Commands (`/camreplay`)

Client-side catalog commands for **ReplayMod** (and live sessions):

| Command | Description |
|---------|-------------|
| `/camreplay list` | List all cameras remembered in the client catalog |
| `/camreplay view <tag>` | Lock replay view onto the remembered camera's sub-level pose |
| `/camreplay rename <from> <to>` | Rename a catalog entry for the current session |

### `CamTag` vs Catalog Rename
- Every camera stores its tag in synched entity data (`CamTag`) and NBT, so newly recorded replays automatically show the real camera name in `/camreplay list` instead of `cam-xxxxxxxx` (legacy cameras saved before `CamTag` existed are backfilled from their entity tags on load).
- `/camreplay rename <from> <to>` is a client-side override for the current session and is never overwritten by periodic catalog refreshes.

### Using `/camreplay` with CommandKeys in ReplayMod
Because ReplayMod does not open the normal chat command line during playback, you can either:
1. **Right-click** a camera anchor entity once in ReplayMod to capture it, then use the **Left / Right Arrow** keys to cycle through all remembered cameras in the catalog.
2. Use a mod like **CommandKeys** to bind keys directly to `/camreplay view <tag>` or `/camreplay list`.

---

## Keybinds & Controls

All keybinds are configurable under **Options → Controls → Key Binds → Sable Camera Anchor** and are only active while viewing a camera:

| Action | Default Key | Behavior |
|--------|-------------|----------|
| **Exit Camera View** | `Backspace` | Exits live camera view (or clears active `/camreplay` cache) |
| **Previous Camera** | `Left Arrow` | Cycles to the previous camera (works in both live view and ReplayMod) |
| **Next Camera** | `Right Arrow` | Cycles to the next camera (works in both live view and ReplayMod) |
| **Lock/Unlock Camera Aim** | `L` | Toggles between **LOCKED** (mouse controls your player) and **UNLOCKED** (mouse aims the camera) |
| **Decrease Camera FOV (-1)** | `-` | Decreases FOV by `1°` while camera aim is **UNLOCKED** |
| **Increase Camera FOV (+1)** | `=` | Increases FOV by `1°` while camera aim is **UNLOCKED** |
| **Scroll Wheel** *(while unlocked)* | `Scroll Up / Down` | Zooms camera FOV in (`-1°`) / out (`+1°`) without scrolling your hotbar |

### Mouse Aim & FOV Workflow
1. Enter a camera with `/camanchor view <tag>`. An HUD indicator in the top-left displays `CAM: <tag> | FOV: <fov>° | LOCKED` (hidden when `F1` hides the GUI).
2. While **LOCKED**, you can walk, jump, crouch, break blocks, and flip levers as your character while watching yourself from the ship camera.
3. Press **`L`** to switch to **UNLOCKED**. Move your mouse to aim the camera (pitch/yaw) and use the **scroll wheel** or **`-` / `=`** to dial in the FOV.
4. Press **`L`** again to **LOCK & save**. Operators automatically sync and persist the new angle and FOV to the server camera entity; in ReplayMod, the updated pose is saved to the session catalog.

---

## Live Viewing vs ReplayMod Spectate

- **Live Viewing (`/camanchor view <tag>`):**
  - Uses an "outside" camera override: your player stays in their original gamemode (Survival, Creative, etc.) and position.
  - Your local player model is rendered in the world, while first-person hand rendering, crosshair, block outlines, view bobbing, and hurt tilt are suppressed.
  - Orientation is driven directly via quaternion slerp from Sable's `renderPose()`, avoiding Euler gimbal lock or ±90° pitch snaps.
- **ReplayMod Viewing:**
  - Right-clicking a camera anchor entity in ReplayMod spectates it and caches its sub-level attachment in `ReplayFollowCache`, keeping the camera locked to the ship even across timeline scrubs.
  - Press **Shift (Crouch)** to exit ReplayMod entity spectating, or use **Left / Right Arrow** to cycle between cameras in `CameraCatalog`.

---

## Sub-Level Streaming, Multiplayer & Far Viewing (~4 km)

- **Server Forceloading:** For a camera to track a ship when no player is near it, the sub-level's chunks on the server must remain loaded (e.g. via `/forceload`). If `/camanchor view` is used while the ship's sub-level is unloaded on the server, a warning is shown and the view stays on your player until the sub-level loads.
- **Client Sub-Level Streaming (`SSRD` / `EEAAO`):**
  - When the ship's sub-level is streamed to the client, the camera uses Sable's 60+ FPS interpolated `renderPose()` for buttery-smooth tracking.
  - When viewing a camera from far away (beyond normal Sable client tracking range), the server streams a 20 Hz world-pose fallback (`CameraPosePublisher`). Because the fallback updates at server tick rate without client-side sub-level interpolation, it can appear slightly jittery—installing **`SSRD` / `EEAAO`** keeps the sub-level rendered at long range and restores full client-side smoothness.

---

## License

MIT
