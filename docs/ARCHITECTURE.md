# VoidRP Auth Bridge Architecture

## Goal
This mod is intended to bridge three authentication flows:
1. launcher play ticket -> automatic auth on join
2. backend validation -> authoritative account resolution
3. legacy /login fallback -> compatibility for old players

## Suggested package map
- `bootstrap` - composition root and service wiring
- `client` - local ticket loading and client->server dispatch
- `server` - backend calls, auth state, legacy login flow
- `common.dto` - shared request/response and ticket models
- `integration` - adapters into other existing mods/plugins/systems
- `config` - runtime paths and backend endpoint settings

## Next implementation steps
1. Add real packet payload registration for NeoForge 1.21.1.
2. Bind client dispatcher to the actual login lifecycle event.
3. Bind server consume service to a packet receiver.
4. Add `/login <password>` server command.
5. Call `AuthRestrictionBridge` to unlock restricted actions.
6. Clear auth state on disconnect.
