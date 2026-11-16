# Features

How Almanac covers the Google Calendar app, screen by screen.

## Main screen

| Google Calendar | Almanac |
|---|---|
| Menu with views (Schedule, Day, 3 days, Week, Month) | Same; the choice is remembered |
| Calendar list with colored checkboxes per account | Same, plus Birthdays and phone-only calendars |
| Month name in the top bar opens a mini month | Same (not in Month view); dots mark busy days |
| Today button with today's date | Same |
| Search | Searches titles, places, descriptions and guests across ±2–3 years |
| + button | Opens a new event at the next whole hour |
| Pull to refresh | In Schedule; asks DAVx5 to sync now |
| Month illustrations in Schedule | Seasonal colored banners |
| Tasks, Goals, Working location, Google Meet | Not included (events only; no Google services) |

## Views

- **Schedule:** endless list of days with events, week ranges, month banners,
  a red line at the current time, "Nothing planned" on an empty today.
- **Day / 3 days / Week:** 24-hour grid. All-day and 24-hour+ events sit on
  top, overlapping events share the width, and a red line marks now. Pinch to
  zoom, swipe for the next period, tap a free slot to add an event there, tap
  a day header to open that day.
- **Month:** full month grid, event chips with "+N" when a day is full, tap a
  day to open it, swipe between months. Optional week numbers.

## Event details

Color, title, date and time (plus the event's own zone if it differs), repeat
rule, location (opens maps), guests with replies and organiser, Email guests,
notifications, description with tappable links, calendar and account,
busy/free and visibility. Menu: Delete, Duplicate, Share. If you are a guest
you can answer **Going? Yes / No / Maybe**.

## Editing

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
| General | Theme, start of week, week numbers, show declined events, dim past events, default event duration |
| Notifications | On/off, default notification, default all-day notification, snooze length, Android's notification settings |
| Calendars | Default calendar for new events; rename, recolor or delete phone-only calendars |
