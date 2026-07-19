# MCLinuxBridge

A Bukkit/Spigot plugin that lets OP players and admins run whitelisted Linux shell commands in-game.

## Requirements

- Spigot / Paper / Purpur 1.8+
- Java 8+

## Installation

1. Drop the `.jar` into your server's `plugins/` folder
2. Restart the server
3. Edit `plugins/MCLinuxBridge/config.yml` to configure the whitelist
4. Run `/mclinux help` in-game

## Permissions

| Permission | Description |
|---|---|
| `mclinux.admin` | Access to all /mclinux commands |

OP players always have access regardless of permissions.

## Commands

| Command | Description |
|---|---|
| `/mclinux help` | Shows all commands |
| `/mclinux <command>` | Runs a whitelisted shell command |
| `/mclinux list [path]` | Lists files in a directory |
| `/mclinux cat <file>` | Reads a file |
| `/mclinux disk` | Shows storage usage |
| `/mclinux reload` | Reloads config.yml |
| `/mclinux cancel` | Kills the active process |

## Config

```yaml
whitelist-enabled: true

whitelisted-commands:
  - "help"
  - "fastfetch"
  - "cancel"
  - "free"
  - "df"
  - "uname"
  - "ls"
  - "cat"