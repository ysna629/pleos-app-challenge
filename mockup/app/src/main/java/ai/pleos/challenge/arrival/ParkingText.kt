package ai.pleos.challenge.arrival

/**
 * Turns STT output into a parking spot, e.g. "지하 오층" -> floor -5, "에이엘 공이" -> AL02.
 * On-device STT writes letters and numbers as Korean words, so map the common spellings back.
 * Anything that does not fit the expected shape returns null and is left to the on-screen picker;
 * we never guess from merged words such as "사회".
 */
object ParkingText {
    private val digits = mapOf(
        '공' to 0, '영' to 0, '일' to 1, '이' to 2, '삼' to 3, '사' to 4, '오' to 5,
        '육' to 6, '륙' to 6, '칠' to 7, '팔' to 8, '구' to 9,
    )

    // Korean spellings of the alphabet, including common STT variants. Longest spellings are tried first.
    private val letters: List<Pair<String, Char>> = listOf(
        "에이치" to 'H', "더블유" to 'W', "더블류" to 'W',
        "에이" to 'A', "애이" to 'A', "비" to 'B', "삐" to 'B', "씨" to 'C', "시" to 'C', "디" to 'D',
        "이" to 'E', "에프" to 'F', "쥐" to 'G', "지" to 'G', "아이" to 'I', "제이" to 'J', "케이" to 'K',
        "엘" to 'L', "엠" to 'M', "엔" to 'N', "오" to 'O', "피" to 'P', "큐" to 'Q', "알" to 'R',
        "에스" to 'S', "티" to 'T', "유" to 'U', "브이" to 'V', "엑스" to 'X', "와이" to 'Y',
        "제트" to 'Z', "젯" to 'Z',
    ).sortedByDescending { it.first.length }

    private val fillerWords = Regex("(기둥|구역|번호|\\s|,)")
    // Only stripped at the end, so "에이" keeps its "에".
    private val fillerSuffix = Regex("(번|쪽|이에요|이요|예요|입니다|이야|요|에|\\.)+$")
    private val hyphenWords = Regex("(다시|하이픈|대시|-)")

    /** Parses an answer to "몇 층이에요?". Negative values are basement floors. */
    fun parseFloor(raw: String): Int? {
        val text = raw.replace(" ", "").removeSuffix(".")
        Regex("^[Bb]([0-9]{1,2})").find(text)?.let { return -it.groupValues[1].toInt() }
        Regex("^비([0-9]{1,2}|[일이삼사오육칠팔구])층?").find(text)?.let { m -> number(m.groupValues[1])?.let { return -it } }
        val match = Regex("(지하|지상)?([0-9]{1,2}|[공영일이삼사오육칠팔구십]{1,3})층").find(text)
            ?: Regex("(지하)([0-9]{1,2}|[일이삼사오육칠팔구십]{1,3})").find(text)
            ?: return null
        val level = number(match.groupValues[2]) ?: return null
        if (level == 0) return null
        return if (match.groupValues[1] == "지하") -level else level
    }

    /**
     * Parses a pillar code: one or more letters, then digits, e.g. "에이 사" -> A4, "에이엘 공이" -> AL02,
     * "비 십오" -> B15, "B 15" -> B15. Leading zeros are kept. A '-' is inserted only when the user said one
     * ("다시", "하이픈" or '-'). Returns null when the answer does not start with a letter.
     */
    fun parsePillar(raw: String): String? {
        var text = raw.replace("제로", "공").replace(fillerWords, "").replace(fillerSuffix, "")
        val hyphen = hyphenWords.containsMatchIn(text)
        text = text.replace(hyphenWords, "")
        if (text.isEmpty()) return null
        Regex("^([A-Za-z]+)([0-9]*)$").find(text)?.let {
            return code(it.groupValues[1].uppercase(), it.groupValues[2], hyphen)
        }
        val code = StringBuilder()
        var rest = text
        while (rest.isNotEmpty()) {
            // Stop reading letters once everything left is a number ("에이 이" -> A2, "에이 오" -> A5).
            if (code.isNotEmpty() && digitString(rest) != null) break
            val ascii = rest.first().takeIf { it in 'A'..'Z' || it in 'a'..'z' }
            if (ascii != null) {
                code.append(ascii.uppercaseChar())
                rest = rest.drop(1)
                continue
            }
            val (spelling, letter) = letters.firstOrNull { rest.startsWith(it.first) } ?: return null
            code.append(letter)
            rest = rest.removePrefix(spelling)
        }
        if (code.isEmpty()) return null
        if (rest.isEmpty()) return code.toString()
        return digitString(rest)?.let { code(code.toString(), it, hyphen) }
    }

    /** Reads a whole sentence such as "지하 오층 에이 사에 주차했어" when the user answers in one go. */
    fun parseSentence(raw: String): Pair<Int?, String?> {
        val floor = parseFloor(raw)
        val afterFloor = raw.substringAfter("층", missingDelimiterValue = raw).replace(parkedVerb, "")
        return floor to parsePillar(afterFloor)
    }

    private val parkedVerb = Regex("(에\\s*)?(주차했어요?|주차함|댔어요?|세웠어요?)\\.?$")
    private val leadingCopula = Regex("^(이고|이야|이에요|이요|예요|입니다|인데|요|야|에)(\\s+|$)")
    private val trailingCopula = Regex("(이에요|이야|이요|예요|입니다|에요|야|요|에)$")

    /**
     * What was said after the floor when it is not a pillar code, e.g. "지하 1층 B동 앞에 주차했어" -> "B동 앞".
     * A building spelled out by STT gets its letter back ("비동 앞" -> "B동 앞"). Null when nothing is left.
     */
    fun placeAfterFloor(raw: String): String? {
        var rest = raw.substringAfter("층", missingDelimiterValue = "").trim().trimEnd('.', ',').replace(parkedVerb, "").trim()
        rest = rest.replace(leadingCopula, "").replace(trailingCopula, "").trim()
        if (rest.length < 2) return null
        val building = letters.firstOrNull { rest.startsWith(it.first + "동") } ?: return rest
        return "${building.second}동" + rest.removePrefix(building.first + "동")
    }

    const val TOWER = "주차타워"
    const val OUTDOOR = "지상 주차"

    /** "3번 타워에 넣었어" -> "주차타워 3번", "기계식이야" -> "주차타워". Null when no tower is mentioned. */
    fun towerLabel(raw: String): String? {
        val text = raw.replace(" ", "")
        if (!text.contains("타워") && !text.contains("기계식")) return null
        val unit = Regex("([0-9]{1,2}|[일이삼사오육칠팔구십]{1,3})(번|호기|호)").find(text)
        val count = unit?.let { number(it.groupValues[1]) }
        return if (count != null) "$TOWER ${count}번" else TOWER
    }

    /** Parked in the open, where there is no floor or pillar to name: "지상이야", "야외 주차장", "길가에 댔어". */
    fun isOutdoor(raw: String): Boolean {
        val text = raw.replace(" ", "")
        if (listOf("야외", "노상", "노면", "길가", "길에", "바깥", "밖에", "옥외").any(text::contains)) return true
        return text.contains("지상") && parseFloor(raw) == null
    }

    fun floorLabel(level: Int) = if (level < 0) "지하 ${-level}층" else "${level}층"

    /** Final text stored in the logbook, e.g. "지하 5층 AL02". */
    fun format(floor: Int?, pillar: String?): String =
        listOfNotNull(floor?.let(::floorLabel), pillar?.takeIf { it.isNotBlank() }).joinToString(" ")

    private val letterNames = mapOf(
        'A' to "에이", 'B' to "비", 'C' to "씨", 'D' to "디", 'E' to "이", 'F' to "에프", 'G' to "지", 'H' to "에이치",
        'I' to "아이", 'J' to "제이", 'K' to "케이", 'L' to "엘", 'M' to "엠", 'N' to "엔", 'O' to "오", 'P' to "피",
        'Q' to "큐", 'R' to "알", 'S' to "에스", 'T' to "티", 'U' to "유", 'V' to "브이", 'W' to "더블유", 'X' to "엑스",
        'Y' to "와이", 'Z' to "제트",
    )
    private val spokenDigits = "공일이삼사오육칠팔구"
    private val codeInText = Regex("([A-Za-z]+)(-?)([0-9]*)")

    /**
     * Rewrites pillar codes for TTS, which reads "A4로" as an English word: "지하 5층 A4로" -> "지하 5층 에이 4로",
     * "AL02" -> "에이엘 공이", "C-3" -> "씨 다시 3". The text shown on screen stays as written.
     */
    fun spoken(text: String): String = codeInText.replace(text) { match ->
        val (letters, hyphen, number) = match.destructured
        val names = letters.uppercase().map(letterNames::getValue).joinToString("")
        val digits = if (number.startsWith("0")) number.map { spokenDigits[it - '0'] }.joinToString("") else number
        listOf(names, if (hyphen.isEmpty()) "" else "다시", digits).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun code(letters: String, digits: String, hyphen: Boolean) =
        if (hyphen && digits.isNotEmpty()) "$letters-$digits" else letters + digits

    /**
     * Digits as written, keeping leading zeros: "공이" -> "02", "4" -> "4". Sino-Korean compounds with 십
     * ("십오" -> "15", "이십일" -> "21") are converted as numbers. Null if anything else is mixed in.
     */
    private fun digitString(word: String): String? {
        if (word.isEmpty()) return null
        if (word.contains('십')) return number(word)?.toString()
        if (word.any { !it.isDigit() && it !in digits }) return null
        return word.map { if (it.isDigit()) it else '0' + digits.getValue(it) }.joinToString("")
    }

    private fun number(word: String): Int? {
        word.toIntOrNull()?.let { return it }
        if (word.isEmpty() || word.any { it != '십' && it !in digits }) return null
        if (!word.contains('십')) return word.fold(0) { acc, c -> acc * 10 + digits.getValue(c) }
        val (tens, ones) = word.split('십', limit = 2)
        if (tens.length > 1 || ones.length > 1 || ones.contains('십')) return null
        return (if (tens.isEmpty()) 1 else digits.getValue(tens.single())) * 10 + (ones.firstOrNull()?.let(digits::getValue) ?: 0)
    }
}
