# Retro-gaming plan

Status: **R1 DONE, R2 WORKING ON THE EMULATOR** (2026-11-01). Nothing is validated on the GP520 yet.

## Progress (2026-11-01)

- FACT (emulator, x86_64): the library lists 3 systems and 1207 games from the
  owner's NAS in about 1 s; Neo Geo covers come from `gamelist.xml`, SNES covers
  from the libretro thumbnail server (104 of 106 found).
- FACT (emulator): Snes9x 2010 downloaded from the build server, loaded with
  `dlopen` from app-private storage (`targetSdk 34`), ran ActRaiser (France) at
  50.0 fps (PAL) with sound: 3904 frames run, 3894 shown, 3 audio underruns (all
  at start), 0 dropped audio frames. A gamepad D-pad press moved the game's menu
  cursor. The picture is 4:3 (1440×1080 on a 1080p screen).
- FACT (emulator): FinalBurn Neo v1.0.0.03 rejects the owner's Neo Geo set: it
  requires `sp-s3.sp1` in `neogeo.zip`, which that older set lacks, even when its
  BIOS option names another BIOS. FB Alpha 2012 Neo Geo (v0.2.97.29) runs the same
  set: 2020 Super Baseball reached its attract mode at 59.18 fps. Coin is Select
  (Create), start is Start (Options).
- FACT (emulator): FinalBurn Neo ran '96 Flag Rally from the arcade folder
  (320x240, 60 fps); coin (Select) and start (Start) reached the car selection.
  How many of the 951 arcade sets FinalBurn Neo accepts is UNKNOWN: many are
  gambling or quiz machines that only MAME emulates.
- Amiga (code in place, NOT_TESTED): the share's `amiga` folder holds TOSEC disk
  images. Disks of one release are grouped into one game, and one release per
  title is kept (complete set first, then the fewest alteration tags, bad dumps
  last). A multi-disk game is copied with an M3U playlist and runs with each disk
  in its own drive (`puae_floppy_multidrive`). The owner's Kickstart files
  (Recalbox full BIOS set) were copied to `amiga/bios` on 2026-11-01.
- NOT_TESTED: two physical pads at once (the code gives each pad its own player
  port), the GP520, save RAM across sessions.

## Goal

Play the owner's own old-console and arcade games on Android TV from a **Retro**
section of GPSx, with the same look, controllers and navigation as the
PlayStation sections. GPSx hosts libretro emulator cores itself (the same core
interface RetroArch uses); it does not embed or launch RetroArch.

## Owner decisions (2026-11-01)

- Games come from the owner's NAS share `\\192.168.1.230\Public\Retrogaming`.
  GPSx never ships, downloads or points to ROMs or BIOS files.
- There will be no commercial use. Cores whose licence forbids commercial use
  (Snes9x, FinalBurn Neo, MAME 2003-Plus…) are therefore acceptable.
- Those cores are **never committed to the repository nor packaged in the APK**:
  GPSx is AGPL-3.0-only and may not add restrictions to what it distributes. The
  app downloads a core on the owner's request from the official libretro build
  server, into its private storage, the way RetroArch does.

- The Retro systems live in the **Consoles** menu under the PS5: one card per system
  found on the share, and a card opens that system's game list with covers. The
  home screen presents PS5, Cloud and Retro side by side. Console pictures are
  original GPSx drawings (no photo, logo or trademark).
- Game covers missing from `gamelist.xml` come from the libretro thumbnail server
  (as in RetroArch). They belong to their publishers and are not free to reuse:
  GPSx ships none; the TV downloads a cover to show the owner's own game and
  keeps it in its cache.

## What is on the share (FACT, read-only listing 2026-11-01)

| Folder | Content | Metadata |
|---|---|---|
| `SNES SFC French Best Set` | 106 zipped SNES games (No-Intro names) | none; names come from file names |
| `neogeo` | 151 games in genre subfolders, `neogeo.zip` BIOS present | `gamelist.xml` (French descriptions), images |
| `mame` | 951 sets, mostly obscure (poker, darts, quizzes), a few CHD disks | `gamelist.xml`, images, videos |

The layout is the EmulationStation one (Recalbox/Batocera): one folder per
system with an optional `gamelist.xml`.

## Order of work

| Step | Content | Exit criterion |
|---|---|---|
| R0 | This plan | Plan written in English and French |
| R1 | **Library**: Retro tab, NAS settings, SMB browsing, `gamelist.xml` parsing, game list per system | Emulator lists the SNES and Neo Geo folders of a test share; JVM tests pass |
| R2 | **Native libretro host** and core installer; SNES with Snes9x 2010 | A SNES homebrew plays on the emulator with picture, sound and pad |
| R3 | GP520 validation (only after the owner opens the hardware gate) | Frame pacing P50/P95/P99 and audio underruns measured on the GP520 |
| R4 | Neo Geo and the playable part of the MAME folder with FinalBurn Neo | `neogeo.zip` found next to the game; a Neo Geo game plays |
| R5 | Save states, SRAM sync, per-system button layout | Save and resume a game across app restarts |

## Architecture

The split follows the project's architecture rules: Kotlin owns UI, lifecycle, settings, network file
access and input capture; the native adapter owns the core, its thread, video and
audio. Frames and samples never cross JNI.

```
Retro tab (Compose)
  └─ RetroLibrary (Kotlin) ── SmbRomSource (jcifs-ng) ── NAS share
        │  game chosen → copy to app cache (unzip for console cores)
        ▼
  RetroSession (Kotlin) ── JNI: load(core, game), start, pause, stop, pad state
        ▼
  retro_host (native adapter, C++)
     core thread: dlopen(core) → retro_run() at the core's rate
     video: retro_video_refresh → ANativeWindow (software blit, RGB565/XRGB8888)
     audio: retro_audio_sample_batch → bounded PCM ring → AAudio callback
     input: retro_input_state ← latest pad state written by JNI on change
```

### Library (R1)

- `com.gpsx.tv.retro` package: `RetroSystem` (folder → system, core, file
  extensions), `GameList` (`gamelist.xml` parser), `RomName` (No-Intro name
  cleanup), `SmbRomSource`, `RetroLibrary` (state holder like
  `CloudPlayController`).
- SMB access uses **jcifs-ng** (LGPL-2.1, compatible with AGPL-3.0 as a separate
  library). The share is opened as guest; if a password is ever needed it is
  stored like the PSN token and never logged.
- System detection is by folder name (`snes`, `SNES…`, `neogeo`, `mame`,
  `megadrive`, `gba`…), case-insensitive; unknown folders are listed as
  "not supported yet".
- The list shows name, year, genre and players from `gamelist.xml` when present,
  else the cleaned file name. Images are loaded lazily over SMB and cached.

### Compatibility as you play

- Each launch records on the TV whether the core accepted the game. For arcade
  sets the load is strict: an error the core logs while loading (a missing ROM)
  counts as a refusal, even when the core would go on to show its own error
  screen. A load that crashes the app is remembered as a refusal at next start.
- Game lists hide refused games by default, with a button to show them again;
  cards show "Compatible" or "Incompatible" once a game has been tried. Games are
  never tested in bulk (owner decision, 2026-11-01).
- Up to 0.2.26 the host crashed on Mega Drive and Master System cartridges, so
  the first start of a later version forgets every crash mark once; a game that
  really crashes marks itself again on its next launch.

### Search

The **Recherche** item of the top bar finds a game by name on every console of
the share. Accents, case and punctuation are ignored; each typed word has to
start a word of the title, in any order, and spaces may be left out ("megaman").
It covers the retro games only; the PS Plus catalog keeps its own search. Refused
games stay listed with their mark, since a search is a deliberate request.

### Storage on the TV and controls

- Each game is copied once from the share into app storage and kept, so it starts
  again without the NAS copy; a copy is complete once a marker names its game file.
  Settings shows the space used (games kept, covers, emulator cores, saves) and the
  free space, with a separate erase for each (owner decision, 2026-11-01).
- The game sheet and the loading screen show a controls diagram for the system:
  each PlayStation button, drawn as a key cap, with its action (SNES B/A/Y/X,
  Neo Geo A–D, arcade buttons 1–6, coin and start) and how to leave the game.

### Native host (R2)

- One core at a time, one dedicated native thread that owns it (libretro cores
  are single-threaded). States: `IDLE → LOADED → RUNNING ⇄ PAUSED → STOPPED`,
  plus `FAILED` with a reason.
- Video: the core's framebuffer is copied into the `ANativeWindow` buffer
  (`ANativeWindow_setBuffersGeometry` at the core's size, the compositor scales
  to the screen). No OpenGL in R2; hardware-rendered cores (N64, PSP) are out of
  scope.
- Audio: the core's samples are resampled linearly to 48 kHz into a bounded
  ring of 4096 stereo frames (about 85 ms). The core thread runs a frame only
  while the ring holds less than 2048 frames, so the audio clock paces the game
  and the ring never drifts. Overflow drops the incoming frames, underflow plays
  silence; both are counted and logged when the game closes. Without an audio
  device the core is paced by its own frame rate.
- Input: the Android controller events already captured for Remote Play are
  mapped once to a platform-neutral pad state, then to the RetroPad
  (Cross→B, Circle→A, Square→Y, Triangle→X, L1/R1/L2/R2, Select/Start, D-pad,
  sticks). JNI is called on state changes, not per frame. Each physical pad gets
  the next free player port (up to 4) the first time it sends a button, so two
  pads play as player 1 and player 2. A press is latched until the next frame
  reads it, so a press shorter than a frame is never lost. The left stick also
  drives the D-pad.
- Leaving a game: Back, or the same L1+R1+Options chord as Remote Play.

### Cores

| System | Core | Licence | Source |
|---|---|---|---|
| SNES | Snes9x 2010 | non-commercial | libretro buildbot, armeabi-v7a / arm64-v8a / x86_64 |
| Neo Geo | FB Alpha 2012 Neo Geo | non-commercial | idem; matches the older romsets Recalbox shipped |
| Arcade | FinalBurn Neo | non-commercial | idem |
| Amiga | PUAE | GPL-2.0 | idem; Kickstart files copied from the share's `amiga/bios` folder |
| Mega Drive, Master System | Genesis Plus GX | non-commercial | idem |
| NES | FCEUmm | GPL-2.0 | idem |
| Game Boy, Game Boy Color | Gambatte | GPL-2.0 | idem |
| Game Boy Advance | mGBA | MPL-2.0 | idem; needs no BIOS file |
| PC Engine | Beetle PCE Fast | GPL-2.0 | idem; CD games need the owner's syscard in `pcengine/bios` |
| PlayStation | PCSX ReARMed | GPL-2.0 | idem; BIOS from `psx/bios` when present, else the core's replacement |

The systems added on 2026-11-07 are NOT_TESTED: the owner's games are not on the share
yet, so no game of these systems has run on the emulator or the GP520. Their folders
(`megadrive`, `mastersystem`, `nes`, `gb`, `gbc`, `gba`, `pcengine`, `psx`) exist on the
share and stay hidden in GPSx until they hold a game. A PlayStation or PC Engine `.cue`
names its tracks, which are read with it.

### Amiga speed on the GP520

FACT (GP520, 0.2.27, 2026-11-07): with the core's default options, four PUAE runs
reached 31 to 38 frames per second instead of 50, with 112 to 4811 audio underruns.
Sound and speed share one cause, as the audio clock waits for a core that cannot
keep up. Mega Drive ran at 58 to 59 frames per second and Neo Geo at 58 in the same
evening. GPSx now asks for low resolution unless a game needs more, one frame in
two drawn, no sound interpolation, no drive noise, and disks read four times faster.
NOT_TESTED on the GP520; the emulator only shows that Speedball 2 still starts.
Brutal Football stops on a Guru Meditation on the emulator, at either disk speed.

The owner chose on 2026-11-07 to keep only Amiga 500 games. A share scan leaves out
AGA, CD32 and CDTV releases, those for an A1200 or A4000, those needing 2 MB or more
or a hard disk, Workbench-only disks, titles known only from bad dumps, and disks that start no game
by themselves (data, save, mission and scenery disks, expansions, updates, editors,
installers, documentation). A game
with both an AGA and an A500 release keeps the latter. This reads file names only:
it cannot tell whether a game really runs, and games are never tested in bulk. The
game sheet has a **Ne marche pas** button for that, which hides the game from its
console list until **Remettre dans la liste**; the search still shows it. Files on
the share are not touched.

### Mega Drive games with CD music

The owner's `megadrive` folder holds one folder per game: a patched `.md`, a `.cue`
and audio tracks (`.bin`, `.wav` or `.ogg`). A `.md` with a `.cue` of the same name
beside it stays on the share with its tracks, like a CD game, and a `.bin` beside a
`.cue` is a track, not a game.

Genesis Plus GX finds the `.cue` by itself from the `.md` path. Two kinds of pack
exist (FACT, core source read 2026-11-07): MD+ packs have `REM LOOP` lines and use
its MegaSD mode, MSU-MD packs have none and use its Mega CD mode. Its automatic
add-on setting picks the right one from the sheet, so GPSx leaves it alone: forcing
the Mega CD silenced MD+ packs. GPSx only sets an NTSC region, as the patches lock
out PAL consoles.

The Mega CD mode needs a BIOS, without which the core drops the CD silently. GPSx
copies `bios_CD_E.bin`, `bios_CD_J.bin` and `bios_CD_U.bin` from `megadrive/bios`
or the owner's Recalbox set, once, under those exact names. If they are missing the
launch stops with a message and the game is not marked as refused. The BIOS files
are never shipped with GPSx.

Evidence, emulator only (2026-11-07, real share, core v1.7.4 f27655b): Sonic 2 runs
past the lockout screen at 60 frames per second, and CD audio reads were measured
as network traffic over 20 s: Gunstar Heroes 159 KiB/s (MD+), Comix Zone 119 KiB/s
and Lemmings 159 KiB/s, near the 172 KiB/s of a CD. No one has listened to it yet.
NOT_TESTED: the GP520, and Moonwalker past its credits, which needs a pad. A file
read from the NAS can stall a frame on a cache miss; audio underruns were 7 to 51
per 33 s run on the emulator, with no comparison yet on the TV.

UNKNOWN: on the emulator the process gains one `sync_file` descriptor per frame
shown, kept after the game closes. GPSx handles no descriptor on that path
(`ANativeWindow_lock` and `unlockAndPost`), so this may come from the emulator's
graphics stack (INFERENCE). It has not been checked on the GP520.

### CD games stay on the share

Owner decision (2026-11-07): heavy games are read from the NAS and never copied to the
TV. `.cue`, `.chd` and `.pbp` images open as `RemoteGame` files: Android's
`openProxyFileDescriptor` (API 26) gives a system descriptor backed by SMB reads, and the
native host offers it to the core through the libretro file interface under a folder
that exists nowhere (`/gpsx-remote/`). Blocks never cross JNI. `ReadAheadFile` keeps 16
blocks of 512 KiB in memory and fetches the next block ahead. A core that does not ask
for the file interface gets an error instead of a silent copy; PCSX ReARMed and Beetle
PCE Fast ask for it in their sources (FACT), which no run has confirmed yet. If the NAS
stops answering, the game stalls or fails: there is no local copy to fall back on.

Discs of one game ("(Disc 2)" tags) are one entry and load through a playlist; L1 + R1 +
Create held 1 s puts the next disc in. Saves take the game's title.

The core installer downloads
`https://buildbot.libretro.com/nightly/android/latest/<abi>/<core>_libretro_android.so.zip`
over HTTPS with normal certificate validation, unzips it into
`filesDir/retro/cores/`, records its SHA-256 and size, and loads it with
`dlopen`. The ABI is the one the process runs (`armeabi-v7a` on the GP520).

## Risks and unknowns

- HYPOTHESIS: `dlopen` of a downloaded core from app-private storage works with
  `targetSdk 34` (Android blocks executing files from app data with `execve`,
  not loading libraries). To check on the emulator in R2.
- HYPOTHESIS: Snes9x 2010 runs at full speed on the GP520's 32-bit ARM CPU.
- Security: a core is native code running inside GPSx. It is downloaded only on
  the owner's request, over HTTPS from the official server, and its hash is shown
  and logged. Nightly builds change, so the hash cannot be pinned in advance.
- UNKNOWN: whether the NAS accepts guest SMB2/3 access from Android, or needs a
  user and password.
- Arcade sets must match the core's romset version; mismatched sets fail to load
  and are reported as such.

## Non-goals

- Shipping, downloading or linking to ROM or BIOS files.
- Hardware-rendered systems (N64, PSP, Dreamcast) and CHD-based arcade games.
- Netplay, achievements, shaders.
