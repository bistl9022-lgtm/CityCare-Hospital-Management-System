package service;

import model.Patient;

/**
 * RULE-BASED PATIENT CLASSIFICATION (not artificial intelligence).
 *
 * The rules are plain if-conditions with fixed weights, written out below so
 * the whole decision can be explained line by line:
 *
 *   1. Critical symptom keyword ................. +50 points
 *   2. Serious symptom keyword .................. +25 points
 *   3. Routine / check-up keyword ............... -15 points
 *   4. Infant (under 5) or senior (over 65) ..... +15 points
 *   5. Child (5-12) or older adult (60-65) ......  +8 points
 *   6. Marked as an emergency case .............. +50 points
 *
 * Final score decides the band:
 *   >= 50  Emergency        -> immediate attention
 *   >= 30  High Priority    -> urgent attention
 *   >= 12  Medium Priority  -> normal medical attention
 *   else   Low Priority     -> routine attention
 *
 * A priority chosen manually by a doctor always overrides the computed one.
 */
public class ClassificationService {

    public static final String EMERGENCY = "Emergency";
    public static final String HIGH      = "High Priority";
    public static final String MEDIUM    = "Medium Priority";
    public static final String LOW       = "Low Priority";

    public static final String[] LEVELS = { EMERGENCY, HIGH, MEDIUM, LOW };

    private static final String[] CRITICAL = {
        "chest pain", "cardiac", "heart attack", "unconscious", "stroke", "seizure",
        "severe bleeding", "bleeding", "breathless", "shortness of breath", "accident",
        "trauma", "poison", "burn", "coma", "paralysis"
    };

    private static final String[] SERIOUS = {
        "high fever", "fracture", "infection", "pneumonia", "asthma", "dehydration",
        "severe", "injury", "surgery", "diabetes", "hypertension", "kidney"
    };

    private static final String[] ROUTINE = {
        "routine", "checkup", "check-up", "follow up", "follow-up", "consultation",
        "vaccination", "general", "minor"
    };

    /** Outcome of the rules: the band, the score and the reasons that produced it. */
    public static class Result {
        public final String level;
        public final int    score;
        public final String reason;

        public Result(String level, int score, String reason) {
            this.level = level;
            this.score = score;
            this.reason = reason;
        }

        public String action() { return recommendedAction(level); }
    }

    /** Convenience overload used by the Patients screen. */
    public static Result classify(Patient p) {
        return classify(p.getAge(), p.getDisease(), false, null);
    }

    /**
     * @param age              patient age in years
     * @param symptoms         disease / problem text typed by the user
     * @param emergencyCase    true when the patient arrived as an emergency
     * @param doctorPriority   priority chosen by the doctor, or null / "Auto"
     */
    public static Result classify(int age, String symptoms, boolean emergencyCase, String doctorPriority) {

        // Rule 0 - a doctor's explicit choice wins over everything else.
        if (doctorPriority != null && !doctorPriority.trim().isEmpty()
                && !doctorPriority.equalsIgnoreCase("Auto")) {
            return new Result(doctorPriority, 100, "Priority set manually by the attending doctor.");
        }

        String text = symptoms == null ? "" : symptoms.toLowerCase();
        int score = 0;
        StringBuilder why = new StringBuilder();

        String hit = firstMatch(text, CRITICAL);
        if (hit != null) { score += 50; why.append("- Critical symptom detected: \"").append(hit).append("\" (+50)\n"); }

        hit = firstMatch(text, SERIOUS);
        if (hit != null) { score += 25; why.append("- Serious condition detected: \"").append(hit).append("\" (+25)\n"); }

        hit = firstMatch(text, ROUTINE);
        if (hit != null) { score -= 15; why.append("- Routine / check-up case: \"").append(hit).append("\" (-15)\n"); }

        if (age > 0 && (age < 5 || age > 65)) {
            score += 15;
            why.append("- Vulnerable age group (").append(age).append(" years) (+15)\n");
        } else if (age >= 5 && age <= 12 || age >= 60 && age <= 65) {
            score += 8;
            why.append("- Sensitive age group (").append(age).append(" years) (+8)\n");
        }

        if (emergencyCase) { score += 50; why.append("- Registered as an emergency case (+50)\n"); }

        if (why.length() == 0) why.append("- No critical or serious keyword matched.\n");

        String level;
        if (score >= 50)      level = EMERGENCY;
        else if (score >= 30) level = HIGH;
        else if (score >= 12) level = MEDIUM;
        else                  level = LOW;

        why.append("\nTotal score: ").append(score).append("  ->  ").append(level);
        return new Result(level, score, why.toString());
    }

    public static String recommendedAction(String level) {
        if (EMERGENCY.equals(level)) return "Immediate attention - shift to emergency ward now";
        if (HIGH.equals(level))      return "Urgent attention - see a doctor within the hour";
        if (MEDIUM.equals(level))    return "Normal medical attention - schedule today";
        return "Routine attention - regular OPD appointment";
    }

    private static String firstMatch(String text, String[] keywords) {
        for (int i = 0; i < keywords.length; i++) {
            if (text.contains(keywords[i])) return keywords[i];
        }
        return null;
    }
}
