# Syncing with a CalDAV server

Almanac does not talk to servers itself. A sync app copies calendars between
the server and the phone's calendar store, and Almanac shows and edits what is
there. On Android the open-source choice is **DAVx5**.

## Setting up DAVx5

1. In DAVx5 tap **+** and choose **Login with URL and user name**.
2. Enter the server's address (for Davit: `https://<your host>/dav/`), your
   user name and an app or sync password.
3. On the account's **CalDAV** tab, tick the calendars to sync.

The calendars then appear in Almanac's menu, grouped by account. New calendars
on the server are created in DAVx5 (the **+** on the CalDAV tab) or on the
server.

## What syncs

| In Almanac | On the server |
|---|---|
| Events, all-day events, time zones | VEVENT with DTSTART/DTEND/TZID |
| Repeat rules, "this and following" | RRULE (with UNTIL or COUNT) |
| Changing or deleting one occurrence | RECURRENCE-ID exception, or EXDATE plus a separate event for a series that has not synced yet |
| Guests and replies | ATTENDEE / ORGANIZER with PARTSTAT |
| Notifications | VALARM |
| Event color | COLOR (if DAVx5 offers colors for the account) |

**Refresh** in the menu, or pulling down the schedule, asks DAVx5 to sync now.

## Invitations

Adding guests stores them on the event. The **server** sends the invitations
(Davit does, by email). A calendar app on the phone does not. Replies you make
with Yes / No / Maybe are stored as your attendee status and synced, and the
server tells the organiser.

## Phone-only calendars

**Add calendar** in the menu makes a calendar that lives only on this phone.
It is handy for testing or private notes, but it is not synced or backed up
anywhere.

## Notifications twice?

Every calendar app on the phone is told when a reminder is due. If Google
Calendar (or another calendar app) is installed, turn off its notifications,
or turn off **Event notifications** in Almanac's settings.
