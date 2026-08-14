# Sable Camera Anchor

Minimal camera system for **Sable / Create Aeronautics**.

## Features

- Custom `CameraAnchor` entity that can be spectated
- Custom tags for easy identification and switching
- Tiltable head (pitch + yaw)
- Attempts to attach to the Sable sub-level you are standing on when spawned
- Easy spawn / kill / pose commands

## Commands

All commands require permission level 2.

```mcfunction
# Spawn a camera at your position with a tag
/cameranchor spawn <tag>

# Example
/cameranchor spawn front
/cameranchor spawn side
/cameranchor spawn top

# Set head pose (pitch, yaw) on cameras with a specific tag (or all if no tag)
/cameranchor pose <pitch> <yaw> [tag]

# Example – look 25° down
/cameranchor pose 25 0 front

# Kill cameras
/cameranchor kill          # kills all
/cameranchor kill front    # kills only tagged ones
```

## Spectating

```mcfunction
/spectate @e[type=sablecamera:camera_anchor,tag=front,limit=1]
```

## Notes

- Spawn the camera **while standing on the assembled ship** so it can try to attach to the sub-level.
- This is still an early version. Full quaternion-based orientation (including roll) will be added later.
- The entity is invisible, has no gravity, and cannot be collided with.

## Building

Requires:
- NeoForge 1.21.1 (21.1.x)
- Sable 2.0.3+
- Java 21