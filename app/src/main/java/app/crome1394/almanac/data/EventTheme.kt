package app.crome1394.almanac.data

/**
 * Picture themes for events, chosen from words in the title (as Google Calendar does for
 * "dinner", "birthday", "flight" and so on). Earlier entries win, so the specific ones come
 * first ("birthday dinner" is a birthday). Words match whole, ignoring case, and a trailing
 * plural "s" is allowed.
 */
enum class EventTheme(vararg val words: String) {
    BIRTHDAY("birthday", "bday", "b-day"),
    ANNIVERSARY("anniversary", "wedding", "engagement", "valentine", "valentines", "date night"),
    CHRISTMAS("christmas", "xmas", "christmas eve", "boxing day", "hanukkah", "chanukah"),
    NEW_YEAR("new year", "new year's", "new years", "nye", "hogmanay"),
    HALLOWEEN("halloween", "trick or treat"),
    THANKSGIVING("thanksgiving", "friendsgiving"),
    EASTER("easter"),
    FLIGHT("flight", "fly", "airport", "plane", "boarding"),
    TRAIN("train", "rail"),
    TRIP("trip", "vacation", "holiday", "travel", "getaway", "road trip", "cruise"),
    BEACH("beach", "seaside", "coast"),
    CAMPING("camping", "camp", "tent"),
    PET("vet", "dog", "cat", "puppy", "walk the dog"),
    HIKE("hike", "hiking", "trek", "walk", "ramble"),
    BREAKFAST("breakfast", "brunch"),
    COFFEE("coffee", "cafe", "café", "tea"),
    LUNCH("lunch"),
    DINNER("dinner", "supper", "restaurant", "meal", "bbq", "barbecue", "pizza"),
    DRINKS("drinks", "beer", "bar", "pub", "cocktail", "cocktails", "wine", "happy hour"),
    PARTY("party", "celebration", "celebrate", "graduation", "reunion"),
    GYM("gym", "workout", "fitness", "training", "crossfit", "weights", "pilates"),
    YOGA("yoga", "meditation", "meditate"),
    RUN("run", "running", "jog", "jogging", "marathon", "5k", "10k", "parkrun"),
    SWIM("swim", "swimming", "pool"),
    BIKE("bike", "cycling", "cycle", "ride", "spin"),
    SOCCER("soccer", "football", "futsal"),
    BASKETBALL("basketball"),
    TENNIS("tennis", "padel", "squash", "badminton", "pickleball"),
    GOLF("golf"),
    SKI("ski", "skiing", "snowboard", "snowboarding"),
    DOCTOR("doctor", "dr", "gp", "checkup", "check-up", "hospital", "clinic", "physio"),
    DENTIST("dentist", "dental", "orthodontist"),
    HAIRCUT("haircut", "hair", "barber", "salon"),
    MOVIE("movie", "film", "cinema", "movies"),
    CONCERT("concert", "gig", "festival", "opera", "band", "music"),
    THEATRE("theatre", "theater", "play", "musical", "show", "ballet"),
    SHOPPING("shopping", "groceries", "grocery", "market", "mall"),
    STUDY("class", "lesson", "lecture", "school", "exam", "study", "course", "homework"),
    READING("book club", "reading", "library"),
    CLEANING("cleaning", "clean", "laundry", "chores"),
    GARDEN("garden", "gardening", "plant", "planting"),
    CAR("car", "mechanic", "garage", "mot", "oil change", "car wash"),
    BILLS("bill", "bills", "rent", "tax", "taxes", "budget", "payday"),
    CODE("hackathon", "deploy", "code review"),
    ;

    companion object {
        private val patterns: List<Pair<EventTheme, Regex>> = entries.map { t ->
            t to Regex(
                t.words.joinToString("|", prefix = "(?<![\\p{L}\\p{N}])(?:", postfix = ")s?(?![\\p{L}\\p{N}])") { Regex.escape(it) },
                RegexOption.IGNORE_CASE,
            )
        }

        /** The theme for an event title, or null when no word matches. */
        fun of(title: String): EventTheme? {
            if (title.isBlank()) return null
            val t = title.take(200)
            return patterns.firstOrNull { (_, r) -> r.containsMatchIn(t) }?.first
        }
    }
}
