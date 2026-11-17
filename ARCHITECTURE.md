# Architecture

Almanac is a single-activity Jetpack Compose app with no network code. All
calendar data lives in Android's calendar store (`CalendarContract`, the
"calendar provider"), which sync adapters such as DAVx5 fill and upload. That
is also how the Google Calendar app works: it is a viewer and editor for the
calendar store, and Google's sync adapter does the syncing.

```
 CalDAV server ⇄ DAVx5 (sync adapter) ⇄ calendar store ⇄ Almanac
                                             │
                                  alarms ────┴──► EVENT_REMINDER ──► notifications
```

## Packages

| Package | Contents |
|---|---|
| `data` | `CalendarRepository` (all reads and writes), the model (`CalendarInfo`, `Instance`, `EventDetails`, `EventDraft`), `RRule` + `RecurrenceText`, `DayLayout` and `MonthLayout` (grid placement), `EventTheme` (picture themes from titles), `VideoCall` (call links), `Ics` (import), `Birthdays`, `ContactsSearch`, `Prefs` |
| `ui` | `AppViewModel` (navigation stack, settings, data access), `MainScreen` (top bar, drawer, FAB), the views (`ScheduleView`, `TimeGridView`, `MonthView`), `EventDetailScreen`, `EditEventScreen` + `Dialogs`, `EventActions` (swipe, hold menu, delete/move questions), `QuickMenu` (the ⋮ button), `Overlays` (search and new-event cards), `Art` (month landscapes and event pictures, drawn in code), search/settings/import, `Fmt` (dates in the phone's language and 12/24-hour setting), theme |
| `notify` | `Notifier` (reads due alerts, posts, snoozes), `ReminderReceiver`, `ReminderActionReceiver` |
| `widget` | Glance schedule and month widgets |
| `src/debug` | `DemoSeeder`: made-up test events; `ArtGallery`: all the artwork on one screen. Debug builds only |

## Data flow

- **Reading.** Screens ask `AppViewModel.instances(from, to)`, which queries
  `Instances` for visible calendars (widened by a day each side, since all-day
  events are stored at UTC midnight) and adds contact birthdays if shown.
  `rememberInstances` reloads whenever `dataVersion` changes; a
  `ContentObserver` on the calendar store bumps it, so syncs and edits from
  other apps show up at once.
- **Writing.** Edits go through the normal app API (not as a sync adapter), so
  the store marks rows dirty and DAVx5 uploads them. Only phone-only
  ("local") calendars are created with sync-adapter rights, which the store
  requires for that account type.
- **Guests** are diffed against the stored list, so unchanged guests keep their
  replies.
- **Cards.** `Screen.isOverlay` marks screens drawn as a card (search, event
  details, the editor). The root draws the last ordinary screen in the stack and the overlay
  above it, so the calendar stays visible underneath.
- **Deletes wait for Undo.** A delete hides the occurrence at once and runs
  five seconds later in the application's scope, so leaving the screen doesn't
  cancel it; if the process dies first, nothing is deleted.
- **Colors.** `Instance.color` and `EventDetails.color` are resolved in the
  repository: an event color key that stands for one of Google's colors
  (`Palette.choices`, nearest by RGB distance) is drawn in Google's shade; an
  event without its own color takes its calendar's color, including one picked
  in Almanac (`Settings.calendarColors`, never written to the store).

## Repeating events

Editing or deleting one occurrence follows Google Calendar's three choices:

| Choice | How it is stored |
|---|---|
| All events | The series row is updated; if the occurrence moved, the series start (and its EXDATEs) move by the same amount |
| This and following | The series gets an `UNTIL` just before the occurrence (a `COUNT` is split between both halves); a new series starts at the occurrence |
| This event, synced series | An exception row via `Events.CONTENT_EXCEPTION_URI` (DTSTART + DURATION only; the store rejects other time columns) |
| This event, unsynced series | The occurrence is added to the series' `EXDATE`; an edited occurrence becomes its own event |

A few rules keep these from losing or doubling occurrences (each has a test in
`RepositoryTest`):

- Editing a changed occurrence (an exception row) for "following" or "all"
  applies to its series with the series' own rule, and moves the series only by
  what changed in this edit.
- Moving a whole series moves its changed and cancelled occurrences' links
  (`ORIGINAL_INSTANCE_TIME`) first, because the store matches exceptions when
  it expands the series.
- Splitting a series copies the left-out dates after the split into the new
  half, and leaves out the dates the old half still holds as changed
  occurrences.
- An event changes calendar by being copied and deleted; repeating events
  can't change calendar.

The last row exists because of how AOSP's calendar store expands occurrences.
It links exception rows to their series only through the series' `_sync_id`,
and a series that has never synced has none. Phone-only calendars never sync,
so their series never get one. Adding an exception to such a series deletes the
series' cached occurrences, and a full rebuild then shows the exception
next to the original occurrence. In the same way, the store's update path
re-expands a series from only the columns passed in, so every update that
touches a series sends the complete set (`seriesValues`).

## Reminders

The calendar store schedules alarms for every reminder and broadcasts
`EVENT_REMINDER` (exempt from Android's background-broadcast limits). The
receiver reads alerts in state SCHEDULED that are due, posts one notification
each and marks them FIRED. Snooze sets an exact alarm of the app's own; it is
remembered in preferences and set again after a reboot. Other calendar apps
also get the broadcast, which is why the README suggests silencing them.

## Security and data safety

- No `INTERNET` permission. Contacts access is optional and asked for only when
  a feature needs it.
- Everything other apps send (insert intents, `.ics` files) is length-capped
  and validated: repeat rules must parse, guest addresses must look like
  addresses, files over 5 MB are refused.
- The only exported receivers are the system's reminder broadcast (which only
  triggers a re-read of the store) and the widgets. The snooze/dismiss
  receiver is not exported. `DemoSeeder` exists only in debug builds.
- Deleting a calendar is offered only for phone-only calendars, behind a
  confirmation; synced calendars are managed in DAVx5 or on the server.
- `allowBackup` is off: the data belongs to the calendar store and the server.
