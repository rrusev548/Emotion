package com.emotion.pet

import android.content.Context

/**
 * Отговори без интернет/ключ — прост "малък мозък" на любимеца.
 */
object SmallBrain {

    fun reply(ctx: Context, input: String, prefs: Prefs): String {
        val t = input.lowercase()
        fun pick(id: Int): String {
            val arr = ctx.resources.getStringArray(id)
            return arr.random()
        }

        return when {
            t.isBlank() -> pick(R.array.brain_default)

            t.startsWith("здрав") || t.startsWith("hi") || t.startsWith("hello") ||
                t.startsWith("хей") || t.startsWith("hey") || t.startsWith("добър") ->
                pick(R.array.brain_greeting)

            t.contains("как си") || t.contains("как сте") || t.contains("how are you") ->
                pick(R.array.brain_how).format(prefs.fullness, prefs.energy)

            t.contains("гладен") || t.contains("яде") || t.contains("храна") ||
                t.contains("ядене") || t.contains("hungry") || t.contains("food") ->
                pick(R.array.brain_food)

            t.contains("игра") || t.contains("play") || t.contains("забав") ->
                pick(R.array.brain_play)

            t.contains("виц") || t.contains("joke") || t.contains("шег") ->
                pick(R.array.brain_joke)

            t.contains("обичам") || t.contains("love") || t.contains("мил") ->
                pick(R.array.brain_love)

            t.contains("благодар") || t.contains("thanks") || t.contains("мерси") ->
                pick(R.array.brain_thanks)

            t.contains("съвет") || t.contains("tip") || t.endsWith("?") ->
                pick(R.array.brain_default)

            else -> pick(R.array.brain_default)
        }
    }

    /** Идле реплики, които любимецът казва сам на екрана. */
    fun idleLine(ctx: Context, prefs: Prefs): String {
        val idleArray = when {
            prefs.sleeping -> return ctx.getString(R.string.pet_sleep_line)
            prefs.fullness < 30 -> R.array.pet_lines_hungry
            prefs.energy < 30 -> R.array.pet_lines_sleepy
            prefs.mood > 80 -> R.array.pet_lines_happy
            else -> R.array.pet_lines_idle
        }
        return ctx.resources.getStringArray(idleArray).random()
    }
}
