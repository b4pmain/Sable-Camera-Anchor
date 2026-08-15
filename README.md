# Sable Camera Anchor

Minimal camera system for **Sable / Create Aeronautics** (NeoForge 1.21.1).

Spawn invisible camera anchors on assembled ships, set their local angle (pitch / yaw / roll), offset them in ship space, and spectate them with a short command. Cameras track the sub-level in position and orientation using Sable’s client-side interpolated pose.

---

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- Sable 2.0.3+
- Java 21

---

## Permission level

**All commands require operator permission level 2.**

This is intentional. The mod:

- Switches the player to spectator mode
- Calls the internal spectate / camera API
- Can restore gamemode and teleport the player back

Those actions are admin-grade. Non-operators cannot use any `/cameranchor` command.

---

## Commands

| Command | Description |
|---------|-------------|
| `/cameranchor addcam <tag>` | Spawn a camera at your position and attach it to the sub-level you are standing on |
| `/cameranchor addcam <tag> <pos>` | Spawn on the block under you at a grid point (`center`, `topleft`, `bottomright`, …) |
| `/cameranchor delcam <tag>` | Delete the camera with that tag |
| `/cameranchor delcam all` | Delete every camera |
| `/cameranchor angle <pitch> <yaw> [roll] [tag]` | Set local look angles (degrees). Optional roll and tag |
| `/cameranchor offset <x> <y> <z> [tag]` | Local-space offset (moves with the ship) |
| `/cameranchor view <tag>` | Save your gamemode + position, switch to spectator if needed, spectate that camera |
| `/cameranchor viewall` | List all camera tags in the world |
| `/cameranchor show true\|false` | Toggle cyan box + look cone debug visuals |

### Placement grid (`addcam … <pos>`)

Looking north (3x3 grid):

```
topleft     topcenter     topright
centerleft  center        centerright
bottomleft  bottomcenter  bottomright
```

Omit `<pos>` to use your exact standing position.

---

## Typical workflow

1. Stand on an **assembled** ship.
2. `/cameranchor addcam front center`
3. `/cameranchor angle 10 0 0 front`
4. `/cameranchor offset 0 1.5 0 front` (optional, ship-local)
5. `/cameranchor view front`
6. Crouch to exit — you return to your previous gamemode and position.

Debug layout:

```mcfunction
/cameranchor show true
```

---

## Behaviour notes

- Cameras must be spawned while you are tracking a Sable sub-level (standing on the assembled ship).
- Attachment data (sub-level id, local offset, angle, offset) is synced to the client.
- Local offsets are stored relative to the sub-level rotation point (float-safe).
- Spectate camera position/orientation is applied client-side each frame from Sable’s `renderPose()`.
- `/cameranchor view` stores your gamemode and position; crouch (or stop spectating) restores both.
- Duplicate tags are rejected on `addcam`.
- `delcam` with no argument does **not** delete everything — it prints an error. Use `delcam all`.

---

## License

MIT