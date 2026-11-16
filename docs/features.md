# Features

How Almanac covers the Google Calendar app, screen by screen.

## Main screen

| Google Calendar | Almanac |
|---|---|
| Menu with views (Schedule, Day, 3 days, Week, Month) | Same; the choice is remembered |
| Calendar list with colored checkboxes per account | Same, plus Birthdays and phone-only calendars |
| Month name in the top bar opens a mini month | Same (not in Month view); dots mark busy days |
| Today button with today's date | Same |
| Search | A card over the calendar; searches titles, places, descriptions and guests across ±2–3 years |
| + button | A ⋮ button instead: a menu above the thumb with Create event, the views, Refresh and Jump to today |
| Toolbar at the top | Or at the bottom (Settings → Toolbar at the bottom) |
| Pull to refresh | In Schedule; asks DAVx5 to sync now |
| Month illustrations in Schedule | An original landscape per month, drawn in layers that drift as you scroll |
| Pictures on dinners, birthdays, flights… | Picture cards for 44 kinds of event, chosen from words in the title |
| Google Meet | A "Join with …" button for Meet, Zoom, Teams, Jitsi, Webex, Whereby, Nextcloud Talk links; creating Meet calls needs Google |
| Tasks, Goals, Working location, Find a time | Not included (see below) |

## Views

- **Schedule:** endless list of days with events, week ranges, month banners,
  a red line at the current time, "Nothing planned" on an empty today. Swipe
  an event right to delete it (Undo for a few seconds); press and hold for
  Open, Edit, Duplicate and Delete; drag the six-dot handle up or down to
  move it to another day (every day shows as a drop target while you drag)
  and sideways to change its time.
- **Day / 3 days / Week:** 24-hour grid. All-day and 24-hour+ events sit on
  top, overlapping events share the width, and a red line marks now. Pinch to
  zoom, swipe for the next period, tap a free slot to add an event there, tap
  a day header to open that day. Press and hold an event and drag it to move
  it (15-minute steps, across days, the grid scrolls at the edges); holding
  without moving opens the event menu.
- **Month:** full month grid; an event spanning several days is one bar,
  "+N" when a day is full; tap a day to open it, swipe between months.
  Optional week numbers.

## Event details

Color, title, date and time (plus the event's own zone if it differs), repeat
rule, location (opens maps), guests with replies and organiser, Email guests,
notifications, description with tappable links, calendar and account,
busy/free and visibility. Menu: Delete, Duplicate, Share. If you are a guest
you can answer **Going? Yes / No / Maybe**.

## Editing

New and existing events are edited in a card from the bottom; drag its
handle to make it taller or shorter, and tap outside to close it (unsaved
changes ask first). Event details open in the same kind of card.

Title, calendar, all-day, start and end (changing the start keeps the length;
an end time before the start on the same day runs past midnight), time zone,
repeat (Daily, Weekly on …, Monthly on the nth weekday or the last one,
Annually, Every weekday, or Custom with interval, weekdays, monthly mode and
ends never / on a date / after N times), guests (contact suggestions when
contacts access is allowed), location, notifications (presets or custom, as
notification or email if the calendar allows), color, description, busy/free
and visibility. Leaving with unsaved changes asks first. Saving a repeating
event asks: this event, this and following events, or all events.

## Reminders

Notification at the reminder time, with **Snooze** (5–60 minutes, set in
Settings), **Dismiss** and **Map**. Tapping it opens the event.

## Other apps

- "Add to calendar" buttons in other apps (`ACTION_INSERT`) open the editor
  pre-filled with title, time, place, description, guests and repeat rule.
- Event links (`content://com.android.calendar/events/…`) open the event, or
  the editor for `ACTION_EDIT`.
- `.ics` files (opened or shared) show the events with a calendar choice
  before adding them.

## Widgets

- **Schedule:** today's date, a + button, and the next 30 days of events.
- **Month:** this month with today marked and dots on busy days.

## Settings

| Group | Settings |
|---|---|
| General | Theme, toolbar at the bottom, start of week, week numbers, show declined events, dim past events, default event duration |
| Notifications | On/off, default notification, default all-day notification, snooze length, Android's notification settings |
| Calendars | Default calendar for new events; rename, recolor or delete phone-only calendars; recolor synced calendars (on this phone only) |

## Colors

Events can take any of Google's 11 colors. Phone-only calendars store them by
name. DAVx5 offers the CSS3 named colors once its **Event colors** setting is
on (account settings, CalDAV); Almanac stores the nearest one and draws it in
Google's shade, so it syncs to the server as a standard `COLOR`. With the
setting off, the editor says so instead of offering colors.

## Not included

- **Tasks:** DAVx5 syncs tasks to a separate tasks app (jtx Board, Tasks.org,
  OpenTasks), and Davit serves events only for now.
- **Goals and Working location:** Google-only features with no CalDAV
  equivalent.
- **Find a time:** needs other people's free/busy times from the server.
