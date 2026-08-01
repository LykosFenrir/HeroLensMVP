package com.herolens.app.core

import kotlin.math.roundToInt

object RecommendationEngine {
    private enum class Bucket { MATCHUP, SYNERGY, MAP, COMFORT, COMPOSITION, RANK_INPUT, SWITCHING, OTHER }
    private data class Contribution(
        val value: Double,
        val reason: Reason? = null,
        val bucket: Bucket = Bucket.OTHER
    )

    private val directCounters: Map<String, Map<String, Double>> = mapOf(
        "dva" to mapOf("pharah" to 3.0, "echo" to 2.7, "bastion" to 2.2, "widowmaker" to 2.0, "ana" to 1.6),
        "doomfist" to mapOf("widowmaker" to 3.0, "ana" to 2.0, "zenyatta" to 2.4, "ashe" to 2.0),
        "orisa" to mapOf("doomfist" to 3.2, "wrecking-ball" to 2.7, "reinhardt" to 2.0, "roadhog" to 1.8),
        "sigma" to mapOf("bastion" to 2.7, "mauga" to 2.3, "widowmaker" to 1.8, "ashe" to 1.5),
        "winston" to mapOf("widowmaker" to 3.4, "ana" to 2.4, "zenyatta" to 2.8, "ashe" to 2.0, "hanzo" to 1.7),
        "zarya" to mapOf("dva" to 2.8, "orisa" to 1.5, "ana" to 1.4, "junker-queen" to 1.8),
        "cassidy" to mapOf("tracer" to 2.8, "genji" to 2.2, "sombra" to 2.2, "pharah" to 1.8, "echo" to 1.6),
        "soldier-76" to mapOf("pharah" to 2.8, "echo" to 2.5, "mercy" to 1.8, "jetpack-cat" to 1.6),
        "ashe" to mapOf("pharah" to 2.4, "echo" to 2.2, "reaper" to 1.3),
        "widowmaker" to mapOf("pharah" to 2.6, "echo" to 2.4, "ashe" to 1.7, "ana" to 1.4),
        "sombra" to mapOf("doomfist" to 3.0, "wrecking-ball" to 3.0, "widowmaker" to 2.0, "sigma" to 1.6, "genji" to 1.5),
        "reaper" to mapOf("winston" to 3.2, "wrecking-ball" to 2.3, "roadhog" to 2.0, "mauga" to 2.0),
        "bastion" to mapOf("reinhardt" to 3.0, "winston" to 2.8, "mauga" to 2.2, "ramattra" to 2.0),
        "mei" to mapOf("doomfist" to 2.5, "wrecking-ball" to 2.7, "genji" to 2.0, "reinhardt" to 1.8),
        "torbjorn" to mapOf("tracer" to 2.7, "sombra" to 2.2, "genji" to 2.0),
        "symmetra" to mapOf("dva" to 2.5, "sigma" to 2.0, "orisa" to 1.8),
        "ana" to mapOf("roadhog" to 3.2, "mauga" to 2.8, "ramattra" to 1.8, "reaper" to 1.7),
        "brigitte" to mapOf("tracer" to 3.2, "genji" to 2.8, "sombra" to 2.5, "doomfist" to 2.4, "wrecking-ball" to 2.5),
        "baptiste" to mapOf("pharah" to 1.8, "echo" to 1.7, "junkrat" to 1.4),
        "kiriko" to mapOf("ana" to 2.8, "junker-queen" to 2.7, "ashe" to 1.4, "mei" to 1.2),
        "lifeweaver" to mapOf("roadhog" to 1.8, "reinhardt" to 1.4, "doomfist" to 1.4),
        "zenyatta" to mapOf("mauga" to 2.2, "roadhog" to 2.0, "orisa" to 1.8, "ramattra" to 1.7)
    )

    private val directSynergies: Map<String, Map<String, Double>> = mapOf(
        "winston" to mapOf("tracer" to 2.5, "genji" to 2.5, "sombra" to 1.8, "ana" to 1.8, "juno" to 1.7),
        "dva" to mapOf("tracer" to 2.0, "genji" to 2.0, "ana" to 1.5, "brigitte" to 1.2),
        "reinhardt" to mapOf("lucio" to 3.0, "mei" to 2.0, "reaper" to 1.8, "baptiste" to 1.7),
        "junker-queen" to mapOf("lucio" to 2.8, "kiriko" to 2.2, "reaper" to 1.5),
        "sigma" to mapOf("widowmaker" to 2.0, "ashe" to 1.7, "baptiste" to 1.5, "zenyatta" to 1.5),
        "mercy" to mapOf("pharah" to 3.2, "echo" to 2.8, "ashe" to 1.8, "soldier-76" to 1.5, "sierra" to 1.6),
        "lucio" to mapOf("reinhardt" to 3.0, "junker-queen" to 2.8, "ramattra" to 1.8, "reaper" to 1.5, "mei" to 1.5),
        "ana" to mapOf("winston" to 1.8, "genji" to 1.8, "reinhardt" to 1.5, "soldier-76" to 1.2),
        "brigitte" to mapOf("ana" to 2.0, "zenyatta" to 2.0, "widowmaker" to 1.2),
        "kiriko" to mapOf("junker-queen" to 2.3, "doomfist" to 1.7, "winston" to 1.5, "genji" to 1.5),
        "zenyatta" to mapOf("sigma" to 1.6, "widowmaker" to 1.5, "hanzo" to 1.5)
    )

    fun recommend(context: MatchContext, limit: Int = 3): List<Recommendation> {
        val allies = context.allyIds.mapNotNull(HeroCatalog.byId::get)
        val enemies = context.enemyIds.mapNotNull(HeroCatalog.byId::get)

        val candidatePool = if (context.allRoles) HeroCatalog.heroes else HeroCatalog.forRole(context.role)

        return candidatePool
            .map { candidate ->
                val contributions = mutableListOf<Contribution>()

                enemies.forEach { enemy ->
                    val direct = directCounters[candidate.id]?.get(enemy.id) ?: 0.0
                    val reverse = directCounters[enemy.id]?.get(candidate.id) ?: 0.0
                    val generic = genericCounter(candidate, enemy)
                    val matchup = (direct + generic - reverse * 0.85) * 2.45
                    if (matchup > 2.2) {
                        contributions += Contribution(
                            matchup,
                            Reason.Counters(
                                enemyName = enemy.name,
                                detail = counterExplanation(candidate, enemy),
                                detailAr = counterExplanationAr(candidate, enemy)
                            ),
                            Bucket.MATCHUP
                        )
                    } else {
                        contributions += Contribution(matchup, bucket = Bucket.MATCHUP)
                    }
                }

                allies.forEach { ally ->
                    val direct = directSynergies[candidate.id]?.get(ally.id)
                        ?: directSynergies[ally.id]?.get(candidate.id)
                        ?: 0.0
                    val synergy = (direct + genericSynergy(candidate, ally)) * 1.8
                    if (synergy > 2.0) {
                        contributions += Contribution(
                            synergy,
                            Reason.WorksWith(
                                allyName = ally.name,
                                detail = synergyExplanation(candidate, ally),
                                detailAr = synergyExplanationAr(candidate, ally)
                            ),
                            Bucket.SYNERGY
                        )
                    } else {
                        contributions += Contribution(synergy, bucket = Bucket.SYNERGY)
                    }
                }

                val mapFit = mapFit(candidate, context.mapProfile) * 2.4
                if (mapFit > 1.9) {
                    contributions += Contribution(mapFit, Reason.MapFit(context.mapProfile.displayName), Bucket.MAP)
                } else {
                    contributions += Contribution(mapFit, bucket = Bucket.MAP)
                }

                val preference = context.preferences[candidate.id] ?: 0
                val comfortScore = when (preference) {
                    1 -> -12.0
                    2 -> 1.5
                    3 -> 7.0
                    4 -> 12.0
                    else -> 0.0
                }
                if (preference > 0) {
                    contributions += Contribution(comfortScore, Reason.Comfort(preference), Bucket.COMFORT)
                }

                val teamNeed = teamNeed(candidate, allies, enemies)
                if (teamNeed != null) contributions += teamNeed

                val rankContribution = rankFit(candidate, context.rank)
                if (rankContribution != null) contributions += rankContribution

                if (context.currentHeroId == candidate.id) {
                    contributions += Contribution(3.0, bucket = Bucket.SWITCHING)
                } else if (context.currentHeroId != null && context.ultimateCharge >= 70) {
                    contributions += Contribution(-7.0, Reason.SwitchCost(context.ultimateCharge), Bucket.SWITCHING)
                }

                val inputContribution = inputFit(candidate, context.inputPlatform)
                if (inputContribution != null) contributions += inputContribution

                val raw = 50.0 + contributions.sumOf { it.value }
                val score = raw.roundToInt().coerceIn(1, 99)
                val rankedReasons = contributions
                    .filter { it.reason != null }
                    .sortedByDescending { it.value }
                    .mapNotNull { it.reason }
                    .distinct()
                val switchWarning = rankedReasons.filterIsInstance<Reason.SwitchCost>().firstOrNull()
                val reasons = rankedReasons.filterNot { it is Reason.SwitchCost }.take(5).toMutableList().apply {
                    if (switchWarning != null) {
                        if (size == 5) removeAt(lastIndex)
                        add(switchWarning)
                    }
                }

                val breakdown = ScoreBreakdown(
                    matchup = bucketScore(contributions, Bucket.MATCHUP),
                    synergy = bucketScore(contributions, Bucket.SYNERGY),
                    map = bucketScore(contributions, Bucket.MAP),
                    comfort = bucketScore(contributions, Bucket.COMFORT),
                    composition = bucketScore(contributions, Bucket.COMPOSITION),
                    rankAndInput = bucketScore(contributions, Bucket.RANK_INPUT),
                    switching = bucketScore(contributions, Bucket.SWITCHING)
                )
                Recommendation(
                    hero = candidate,
                    score = score,
                    reasons = reasons,
                    breakdown = breakdown,
                    playTips = playTips(candidate, allies, enemies),
                    riskNote = riskNote(candidate, enemies)
                )
            }
            .sortedWith(compareByDescending<Recommendation> { it.score }.thenBy { it.hero.name })
            .take(limit)
    }

    private fun genericCounter(candidate: Hero, enemy: Hero): Double {
        var score = 0.0
        if (Trait.ANTI_AIR in candidate.traits && Trait.VERTICALITY in enemy.traits) score += 1.1
        if (Trait.ANTI_DIVE in candidate.traits && Trait.DIVE in enemy.traits) score += 1.25
        if (Trait.PEEL in candidate.traits && Trait.DIVE in enemy.traits) score += 0.85
        if (Trait.SHIELD_BREAK in candidate.traits && Trait.PROTECTION in enemy.traits) score += 0.9
        if (Trait.HITSCAN in candidate.traits && Trait.VERTICALITY in enemy.traits) score += 0.65
        if (Trait.SNIPER in candidate.traits && Trait.CLOSE_RANGE in enemy.traits) score += 0.35
        if (Trait.AREA_CONTROL in candidate.traits && Trait.MOBILITY in enemy.traits) score += 0.45

        if (Trait.CLOSE_RANGE in candidate.traits && Trait.LONG_RANGE in enemy.traits && Trait.MOBILITY !in candidate.traits) score -= 0.55
        if (Trait.PROJECTILE in candidate.traits && Trait.MOBILITY in enemy.traits) score -= 0.25
        return score
    }

    private fun genericSynergy(candidate: Hero, ally: Hero): Double {
        var score = 0.0
        if (Trait.DIVE in candidate.traits && Trait.DIVE in ally.traits) score += 0.75
        if (Trait.BRAWL in candidate.traits && (Trait.BRAWL in ally.traits || Trait.RUSH in ally.traits)) score += 0.7
        if (Trait.POKE in candidate.traits && (Trait.POKE in ally.traits || Trait.SNIPER in ally.traits)) score += 0.65
        if (Trait.SPEED in candidate.traits && Trait.BRAWL in ally.traits) score += 0.8
        if (Trait.PROTECTION in candidate.traits && Trait.SNIPER in ally.traits) score += 0.45
        if (Trait.PEEL in candidate.traits && (Trait.SNIPER in ally.traits || Trait.LONG_RANGE in ally.traits)) score += 0.55
        return score
    }

    private val specialCounterExplanations = mapOf(
        "kiriko|ana" to "Protection Suzu can cleanse anti-heal and interrupt Ana's sleep follow-up.",
        "kiriko|junker-queen" to "Protection Suzu cleanses wound pressure and can deny Rampage's anti-heal window.",
        "winston|widowmaker" to "Jump Pack reaches her angle quickly while Barrier blocks her sightline and support follow-up.",
        "dva|pharah" to "Boosters contest her in the air and Defense Matrix removes rockets during the engagement.",
        "dva|echo" to "Boosters match her vertical movement and Defense Matrix reduces burst projectile pressure.",
        "cassidy|tracer" to "Accurate hitscan and close-range control punish predictable blink paths.",
        "cassidy|genji" to "Hitscan pressure and close-range control make repeated dives much riskier.",
        "soldier-76|pharah" to "Sustained hitscan pressure tracks her flight and forces her away from open angles.",
        "soldier-76|echo" to "Reliable hitscan damage pressures Echo during exposed flight and glide paths.",
        "sombra|doomfist" to "Hack interrupts his engage rhythm and removes key mobility during the punish window.",
        "sombra|wrecking-ball" to "Hack stops his movement and makes his predictable exit path easy to focus.",
        "reaper|winston" to "High close-range damage punishes Winston after he commits and lands inside the team.",
        "ana|roadhog" to "Biotic Grenade blocks self-healing while Sleep Dart punishes predictable hooks and Whole Hog.",
        "brigitte|tracer" to "Shield, knockback and burst healing protect the backline and deny easy one-clips.",
        "brigitte|genji" to "Peel and close-range pressure make it difficult for Genji to finish isolated supports.",
        "zenyatta|mauga" to "Discord Orb amplifies team focus on his large hitbox and forces defensive resources sooner."
    )

    private val specialCounterExplanationsAr = mapOf(
        "kiriko|ana" to "قدرة سوزو تزيل منع العلاج وتقلل فرصة متابعة سهم النوم.",
        "kiriko|junker-queen" to "سوزو تزيل تأثير الجروح ويمكنها إبطال نافذة منع العلاج من الألتميت.",
        "winston|widowmaker" to "يصل إلى موقعها بسرعة بالقفزة ويمنع خط الرؤية بالحاجز.",
        "dva|pharah" to "تلاحقها بالطيران وتمتص الصواريخ بـ Defense Matrix أثناء الاشتباك.",
        "dva|echo" to "تواكب حركتها العمودية وتقلل ضرر المقذوفات بـ Defense Matrix.",
        "cassidy|tracer" to "الهيتسكان والتحكم القريب يعاقبان مسارات الـ Blink المتوقعة.",
        "cassidy|genji" to "ضغط الهيتسكان والتحكم القريب يجعلان الغوص المتكرر أخطر عليه.",
        "soldier-76|pharah" to "ضغط هيتسكان مستمر يتتبع طيرانها ويبعدها عن الزوايا المفتوحة.",
        "soldier-76|echo" to "ضرر هيتسكان ثابت يضغط على Echo أثناء الطيران والانزلاق.",
        "sombra|doomfist" to "الـ Hack يقطع إيقاع دخوله ويوقف حركته في لحظة العقاب.",
        "sombra|wrecking-ball" to "الـ Hack يوقف حركته ويجعل مسار خروجه سهلاً للتركيز.",
        "reaper|winston" to "الضرر العالي من قرب يعاقب Winston بعد القفز داخل الفريق.",
        "ana|roadhog" to "القنبلة تمنع علاجه الذاتي وSleep Dart يعاقب حركاته المتوقعة.",
        "brigitte|tracer" to "الدرع والدفع والعلاج السريع يحمون الخط الخلفي من Tracer.",
        "brigitte|genji" to "الحماية والضغط القريب يصعّبان على Genji إنهاء الدعم المعزول.",
        "zenyatta|mauga" to "Discord Orb يزيد تركيز الفريق على حجمه الكبير ويجبره على استخدام موارده الدفاعية."
    )

    private val specialSynergyExplanations = mapOf(
        "winston|tracer" to "They can collapse on the same isolated target and leave before the enemy stabilizes.",
        "winston|genji" to "Winston creates space and softens grouped targets for Genji's fast cleanup.",
        "reinhardt|lucio" to "Speed Boost closes the distance so Reinhardt can begin the brawl before being poked down.",
        "junker-queen|lucio" to "Speed lets the team stay inside Junker Queen's effective range and chain aggressive engages.",
        "mercy|pharah" to "Guardian Angel maintains the aerial pocket while damage boost increases rocket breakpoints.",
        "mercy|echo" to "Mercy can follow Echo's vertical angles and amplify her burst combos.",
        "ana|genji" to "Long-range healing supports deep angles, and Nano Boost creates a strong blade or neutral-fight window.",
        "ana|winston" to "Ana heals Winston through his engage and Nano Boost strengthens his sustained dive pressure.",
        "brigitte|ana" to "Brigitte protects Ana from flankers so Ana can hold safer sightlines and save cooldowns.",
        "brigitte|zenyatta" to "Brigitte covers Zenyatta's low mobility while Discord helps the team focus her peel target.",
        "sigma|widowmaker" to "Sigma controls long sightlines and buys Widowmaker time to hold an angle.",
        "kiriko|junker-queen" to "Suzu covers aggressive entries while teleport keeps Kiriko connected to the rush."
    )

    private val specialSynergyExplanationsAr = mapOf(
        "winston|tracer" to "يمكنهما الهجوم على نفس الهدف المعزول والخروج قبل أن يستعيد الخصم توازنه.",
        "winston|genji" to "Winston يفتح المساحة ويضعف الأهداف ليُنهيها Genji بسرعة.",
        "reinhardt|lucio" to "Speed Boost يغلق المسافة بسرعة قبل أن يتعرض Reinhardt لضرر الـ poke.",
        "junker-queen|lucio" to "السرعة تبقي الفريق داخل مدى Junker Queen وتدعم الدخول الهجومي المتتابع.",
        "mercy|pharah" to "Mercy تحافظ على الـ pocket الجوي وDamage Boost يزيد ضغط الصواريخ.",
        "mercy|echo" to "Mercy تتابع زوايا Echo العمودية وتزيد ضرر انفجاراتها.",
        "ana|genji" to "العلاج بعيد المدى يدعم دخول Genji وNano Boost يصنع نافذة قوية للألتميت.",
        "ana|winston" to "Ana تعالج Winston أثناء دخوله وNano Boost يقوي ضغط الغوص المستمر.",
        "brigitte|ana" to "Brigitte تحمي Ana من الفلانكرز لتحتفظ Ana بقدراتها وموقعها.",
        "brigitte|zenyatta" to "Brigitte تغطي ضعف حركة Zenyatta وDiscord يساعد الفريق على تركيز هدف الحماية.",
        "sigma|widowmaker" to "Sigma يسيطر على خطوط الرؤية ويمنح Widowmaker وقتاً آمناً للزوايا.",
        "kiriko|junker-queen" to "Suzu تغطي الدخول الهجومي والانتقال يبقي Kiriko متصلة بالفريق."
    )

    private fun counterExplanation(candidate: Hero, enemy: Hero): String {
        specialCounterExplanations["${candidate.id}|${enemy.id}"]?.let { return it }
        return when {
            Trait.ANTI_AIR in candidate.traits && Trait.VERTICALITY in enemy.traits ->
                "Anti-air tools and reliable tracking deny airborne angles and force the target back to cover."
            Trait.HITSCAN in candidate.traits && Trait.VERTICALITY in enemy.traits ->
                "Hitscan pressure tracks exposed airborne movement without travel-time prediction."
            Trait.ANTI_DIVE in candidate.traits && Trait.DIVE in enemy.traits ->
                "Peel and control punish the engage, protect your backline and reduce the diver's escape options."
            Trait.PEEL in candidate.traits && Trait.DIVE in enemy.traits ->
                "Fast defensive pressure protects the target being dived and turns the engage into a punish window."
            Trait.SHIELD_BREAK in candidate.traits && Trait.PROTECTION in enemy.traits ->
                "Sustained pressure removes barriers and defensive resources, opening the enemy formation."
            Trait.AREA_CONTROL in candidate.traits && Trait.MOBILITY in enemy.traits ->
                "Zone control restricts movement lanes and makes mobile escape paths more predictable."
            Trait.DIVE in candidate.traits && Trait.LONG_RANGE in enemy.traits ->
                "Mobility closes the distance quickly and forces the long-range hero away from a safe angle."
            Trait.BURST in candidate.traits && Trait.SUSTAIN in enemy.traits ->
                "Burst damage creates a short elimination window before sustained healing can recover the target."
            else -> "The kit applies reliable pressure to this enemy's preferred range and movement pattern."
        }
    }

    private fun counterExplanationAr(candidate: Hero, enemy: Hero): String {
        specialCounterExplanationsAr["${candidate.id}|${enemy.id}"]?.let { return it }
        return when {
            Trait.ANTI_AIR in candidate.traits && Trait.VERTICALITY in enemy.traits ->
                "أدوات مواجهة الطيران تمنع الزوايا الجوية وتجبر الهدف على العودة للغطاء."
            Trait.HITSCAN in candidate.traits && Trait.VERTICALITY in enemy.traits ->
                "الهيتسكان يتتبع الحركة الجوية المكشوفة بدون الحاجة لتوقع سرعة المقذوف."
            Trait.ANTI_DIVE in candidate.traits && Trait.DIVE in enemy.traits ->
                "الحماية والتحكم يعاقبان الدخول ويحميان الخط الخلفي ويقللان خيارات الهروب."
            Trait.PEEL in candidate.traits && Trait.DIVE in enemy.traits ->
                "الدفاع السريع يحمي الهدف ويحوّل دخول الخصم إلى فرصة لمعاقبته."
            Trait.SHIELD_BREAK in candidate.traits && Trait.PROTECTION in enemy.traits ->
                "الضغط المستمر يكسر الحواجز والموارد الدفاعية ويفتح تشكيل الخصم."
            Trait.AREA_CONTROL in candidate.traits && Trait.MOBILITY in enemy.traits ->
                "السيطرة على المساحة تقلل مسارات الحركة وتجعل الهروب متوقعاً."
            Trait.DIVE in candidate.traits && Trait.LONG_RANGE in enemy.traits ->
                "الحركة السريعة تغلق المسافة وتجبر بطل المدى البعيد على ترك زاويته الآمنة."
            else -> "قدراته تضغط بشكل موثوق على المدى ونمط الحركة الذي يفضله هذا الخصم."
        }
    }

    private fun synergyExplanation(candidate: Hero, ally: Hero): String {
        specialSynergyExplanations["${candidate.id}|${ally.id}"]?.let { return it }
        specialSynergyExplanations["${ally.id}|${candidate.id}"]?.let { return it }
        return when {
            Trait.DIVE in candidate.traits && Trait.DIVE in ally.traits ->
                "Both heroes can engage the same isolated target and disengage on a similar timing."
            Trait.BRAWL in candidate.traits && (Trait.BRAWL in ally.traits || Trait.RUSH in ally.traits) ->
                "Their effective ranges overlap, so the team can commit together and trade resources efficiently."
            Trait.POKE in candidate.traits && (Trait.POKE in ally.traits || Trait.SNIPER in ally.traits) ->
                "Combined long-range pressure controls sightlines and forces defensive cooldowns before the fight."
            Trait.SPEED in candidate.traits && Trait.BRAWL in ally.traits ->
                "Speed closes the gap and helps the brawl hero reach effective range without losing too many resources."
            Trait.PROTECTION in candidate.traits && Trait.SNIPER in ally.traits ->
                "Protection creates safer sightlines and more time for the sniper to take high-value shots."
            Trait.PEEL in candidate.traits && (Trait.SNIPER in ally.traits || Trait.LONG_RANGE in ally.traits) ->
                "Peel protects the ally's angle and lets them keep pressure instead of abandoning position."
            else -> "Their ranges and utility complement each other, giving the team a clearer fight plan."
        }
    }

    private fun synergyExplanationAr(candidate: Hero, ally: Hero): String {
        specialSynergyExplanationsAr["${candidate.id}|${ally.id}"]?.let { return it }
        specialSynergyExplanationsAr["${ally.id}|${candidate.id}"]?.let { return it }
        return when {
            Trait.DIVE in candidate.traits && Trait.DIVE in ally.traits ->
                "يمكن للبطلين الدخول على نفس الهدف المعزول والخروج بتوقيت متقارب."
            Trait.BRAWL in candidate.traits && (Trait.BRAWL in ally.traits || Trait.RUSH in ally.traits) ->
                "مداهما الفعال متقارب، لذلك يستطيع الفريق الالتزام بالقتال معاً."
            Trait.POKE in candidate.traits && (Trait.POKE in ally.traits || Trait.SNIPER in ally.traits) ->
                "ضغط المدى البعيد المشترك يسيطر على خطوط الرؤية ويستهلك قدرات الخصم قبل القتال."
            Trait.SPEED in candidate.traits && Trait.BRAWL in ally.traits ->
                "السرعة تغلق المسافة وتوصل بطل الـ brawl إلى مداه بدون خسارة موارد كثيرة."
            Trait.PROTECTION in candidate.traits && Trait.SNIPER in ally.traits ->
                "الحماية توفر خطوط رؤية أكثر أماناً ووقتاً أطول للتصويب."
            Trait.PEEL in candidate.traits && (Trait.SNIPER in ally.traits || Trait.LONG_RANGE in ally.traits) ->
                "الحماية تحافظ على زاوية الحليف وتسمح له بمواصلة الضغط."
            else -> "المدى والقدرات المساندة يكملان بعضهما ويعطيان الفريق خطة قتال أوضح."
        }
    }

    private fun mapFit(candidate: Hero, profile: MapProfile): Double = when (profile) {
        MapProfile.MIXED -> 0.0
        MapProfile.OPEN -> {
            positive(candidate, Trait.LONG_RANGE, Trait.SNIPER, Trait.POKE, Trait.HITSCAN) -
                positive(candidate, Trait.CLOSE_RANGE) * 0.7
        }
        MapProfile.VERTICAL -> positive(candidate, Trait.VERTICALITY, Trait.MOBILITY, Trait.DIVE) -
            positive(candidate, Trait.CLOSE_RANGE) * 0.2
        MapProfile.CLOSE -> positive(candidate, Trait.BRAWL, Trait.CLOSE_RANGE, Trait.AREA_CONTROL, Trait.RUSH) -
            positive(candidate, Trait.SNIPER) * 0.8
    }

    private fun positive(hero: Hero, vararg traits: Trait): Double =
        traits.count { it in hero.traits } * 0.55

    private fun teamNeed(candidate: Hero, allies: List<Hero>, enemies: List<Hero>): Contribution? {
        val enemyDive = enemies.count { Trait.DIVE in it.traits || Trait.MOBILITY in it.traits }
        val allyProtection = allies.count { Trait.PROTECTION in it.traits || Trait.PEEL in it.traits }
        if (enemyDive >= 2 && allyProtection == 0 && (Trait.PEEL in candidate.traits || Trait.ANTI_DIVE in candidate.traits)) {
            return Contribution(4.0, Reason.TeamNeed("peel against enemy dive"), Bucket.COMPOSITION)
        }

        val allyBrawl = allies.count { Trait.BRAWL in it.traits || Trait.CLOSE_RANGE in it.traits }
        if (allyBrawl >= 2 && Trait.SPEED in candidate.traits) {
            return Contribution(3.4, Reason.TeamNeed("speed for the brawl composition"), Bucket.COMPOSITION)
        }

        val enemyProtection = enemies.count { Trait.PROTECTION in it.traits }
        if (enemyProtection >= 2 && Trait.SHIELD_BREAK in candidate.traits) {
            return Contribution(3.2, Reason.TeamNeed("pressure into layered protection"), Bucket.COMPOSITION)
        }

        return null
    }
    private fun rankFit(candidate: Hero, rank: String): Contribution? {
        if (rank == "UNRANKED") return null
        val lowRank = rank in setOf("BRONZE", "SILVER", "GOLD")
        val highRank = rank in setOf("MASTER", "GRANDMASTER", "CHAMPION")
        val score = when {
            lowRank -> {
                var value = 0.0
                if (Trait.SUSTAIN in candidate.traits) value += 0.9
                if (Trait.AREA_CONTROL in candidate.traits) value += 0.6
                if (Trait.BRAWL in candidate.traits) value += 0.4
                if (Trait.SNIPER in candidate.traits && Trait.MOBILITY !in candidate.traits) value -= 0.35
                value
            }
            highRank -> {
                var value = 0.0
                if (Trait.MOBILITY in candidate.traits) value += 0.65
                if (Trait.PEEL in candidate.traits || Trait.CLEANSE in candidate.traits) value += 0.55
                if (Trait.LONG_RANGE in candidate.traits || Trait.POKE in candidate.traits) value += 0.4
                value
            }
            else -> {
                var value = 0.0
                if (Trait.SUSTAIN in candidate.traits) value += 0.35
                if (Trait.MOBILITY in candidate.traits) value += 0.35
                value
            }
        }
        return if (score >= 0.8) Contribution(score * 2.0, Reason.RankFit(rank.lowercase().replaceFirstChar(Char::uppercase)), Bucket.RANK_INPUT)
        else if (score != 0.0) Contribution(score * 2.0, bucket = Bucket.RANK_INPUT)
        else null
    }

    private fun bucketScore(contributions: List<Contribution>, bucket: Bucket): Int =
        contributions.filter { it.bucket == bucket }.sumOf { it.value }.roundToInt().coerceIn(-20, 20)

    private fun inputFit(candidate: Hero, inputPlatform: String): Contribution? {
        val console = inputPlatform.equals("CONSOLE", ignoreCase = true)
        val value = when {
            console && Trait.SUSTAIN in candidate.traits -> 0.65
            console && Trait.AREA_CONTROL in candidate.traits -> 0.55
            console && Trait.SNIPER in candidate.traits && Trait.MOBILITY !in candidate.traits -> -0.35
            !console && Trait.HITSCAN in candidate.traits -> 0.35
            !console && Trait.MOBILITY in candidate.traits -> 0.25
            else -> 0.0
        }
        return when {
            value >= 0.6 -> Contribution(value * 1.8, Reason.InputFit(if (console) "Console" else "PC"), Bucket.RANK_INPUT)
            value != 0.0 -> Contribution(value * 1.8, bucket = Bucket.RANK_INPUT)
            else -> null
        }
    }

    private fun playTips(candidate: Hero, allies: List<Hero>, enemies: List<Hero>): List<String> = buildList {
        val airborne = enemies.firstOrNull { Trait.VERTICALITY in it.traits }
        val diver = enemies.firstOrNull { Trait.DIVE in it.traits || Trait.MOBILITY in it.traits }
        val sniper = enemies.firstOrNull { Trait.SNIPER in it.traits || Trait.LONG_RANGE in it.traits }
        when {
            airborne != null && (Trait.HITSCAN in candidate.traits || Trait.ANTI_AIR in candidate.traits) ->
                add("Hold a clean sightline on ${airborne.name}; pressure during exposed flight instead of chasing through cover.")
            diver != null && (Trait.PEEL in candidate.traits || Trait.ANTI_DIVE in candidate.traits) ->
                add("Save one defensive cooldown for ${diver.name}'s engage and punish the exit, not only the entry.")
            sniper != null && Trait.DIVE in candidate.traits ->
                add("Use cover to close distance, then force ${sniper.name} off the angle before committing deeper.")
        }
        if (Trait.BRAWL in candidate.traits || Trait.CLOSE_RANGE in candidate.traits) {
            add("Fight from cover and corners so your team reaches effective range without spending every resource first.")
        } else if (Trait.POKE in candidate.traits || Trait.LONG_RANGE in candidate.traits) {
            add("Create crossfire from a safe angle and rotate before the enemy closes the distance.")
        }
        val matchingAlly = allies.firstOrNull { genericSynergy(candidate, it) > 0.8 }
        if (matchingAlly != null) {
            add("Time your pressure with ${matchingAlly.name}; the pairing is strongest when both commit to the same target or space.")
        }
        if (Trait.MOBILITY in candidate.traits) {
            add("Keep one movement option for disengaging after the first cooldown trade.")
        } else if (Trait.PROTECTION in candidate.traits || Trait.SUSTAIN in candidate.traits) {
            add("Anchor near cover and preserve defensive resources for the enemy's strongest burst window.")
        }
    }.distinct().take(3)

    private fun riskNote(candidate: Hero, enemies: List<Hero>): String? {
        val hardThreat = enemies.maxByOrNull { enemy ->
            (directCounters[enemy.id]?.get(candidate.id) ?: 0.0) + genericCounter(enemy, candidate)
        } ?: return null
        val severity = (directCounters[hardThreat.id]?.get(candidate.id) ?: 0.0) + genericCounter(hardThreat, candidate)
        return if (severity >= 2.2) {
            "Watch ${hardThreat.name}: it can punish this pick if you lose cover, range control or key cooldowns."
        } else null
    }

}
