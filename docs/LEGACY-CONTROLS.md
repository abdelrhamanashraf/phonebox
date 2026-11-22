# Discord sounds and instant replay

The Discord widget can show sounds returned by the connected desktop Discord client. Enable control on the widget, connect Discord through the agent, join a voice channel, then expand **Discord sounds**. Choose a server and tap a sound. Discord's own permissions, voice state, and external-sound restrictions still apply. The local RPC soundboard commands used by this integration are not in Discord's main public RPC reference and can change between Discord releases.

The **Soundboard** section in the same widget uses the separate sound controls configured in the computer's Control Deck. These files play on a computer output device; sending them through a Discord microphone needs separate audio routing.

To add an NVIDIA Instant Replay button without changing application code, add a PhoneBox shortcut whose command is `builtin:keys` and whose arguments are `alt+f10`. Add a Big button widget pointing to that shortcut, or use the Shortcut grid. The shortcut must match NVIDIA's configured save-replay hotkey, and Instant Replay must already be enabled. A successful key request does not verify that NVIDIA saved a clip.

The older Android experiment and its pairing steps are documented in [experimental/legacy-android](../experimental/legacy-android/README.md).
