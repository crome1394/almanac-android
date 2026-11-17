# Changelog

## 0.1.0 (unreleased)

First version.

- Schedule, Day, 3-day, Week and Month views with swiping, pinch zoom, a "now"
  line, mini month and Today button.
- Event details with guests and replies, Yes/No/Maybe, email guests, map,
  links, share, duplicate and delete.
- Event editor: all-day, times, time zone, repeat (presets and custom), guests
  with contact suggestions, location, notifications, color, description,
  busy/free and visibility.
- Repeating events: change or delete this event, this and following, or all.
- Reminder notifications with Snooze, Dismiss and Map.
- Search; birthdays from contacts; phone-only calendars.
- Opens "add to calendar" requests, event links and `.ics` files.
- Schedule and month home-screen widgets.
- Settings: theme, start of week, week numbers, declined events, dimming past
  events, default duration and notifications, snooze length, default calendar.
- Schedule: an illustrated landscape for each month whose layers drift as you
  scroll; the month name in the top bar rolls when the month changes.
- Picture cards in Schedule for dinners, birthdays, flights, holidays and 40
  other kinds of event, chosen from words in the title.
- Swipe an event right in Schedule to delete it, with Undo (it must be a
  clearly sideways swipe across 40% of the card; a buzz marks the point where
  letting go deletes); press and hold any
  event for Open, Edit, Duplicate and Delete.
- Drag events to another time or day in the Day, 3-day and Week views.
- Month view and the all-day row of Day, 3-day and Week draw an event spanning
  several days as one bar.
- Google's 11 event colors on every calendar: matched to DAVx5's color names
  when its "Event colors" setting is on, with a hint when it is off.
- Recolor synced calendars (kept in Almanac on this phone).
- "Join with …" button for Meet, Zoom, Teams, Jitsi and other call links.
- The `.ics` import preview shows how each event repeats.
- Drag events to another day in Schedule (by the handle, or press and hold the
  card then move); a six-dot handle marks every event
  that can be moved (in the grid it starts a drag without the long press).
- A floating ⋮ button opens a menu above the thumb: Create event, the five
  views and Refresh.
- Settings → Toolbar at the bottom, for one-handed use.
- Search, event details, new events and edits open as cards over the
  calendar; drag a card's handle to resize it.
- In Schedule, slide sideways while dragging an event to change its time.
- Jump to today always returns to today, and scrolls the grid to now.
- New app icon: a calendar page with a crescent moon.
- Day/3-day/week: drag an event's bottom edge to change its end; press, hold
  and drag on empty time to create an event of that length.
- Month: drag events to another day; "+N" opens a list of that day's events.
- Event card: Going? Yes / No / Maybe at the top for invitations, and a row of
  color circles to recolor the event in one tap.
- Turning on All-day for an event under a day long keeps it to that one day.
- Code review fixes: repeating-event edits that could drop or double
  occurrences; a crash resizing late-night events; `.ics` files on Android
  10–12; colors kept on unrelated edits; changing an event's calendar.
