package fr.citedesiles.citeplugin.leaderboard;

/**
 * Formateurs d'affichage des valeurs de stats pour les classements.
 */
public final class StatFormatters {

    private StatFormatters() {
    }

    /** Temps de jeu en secondes → format lisible « Xh Ymin » / « Ymin ». */
    public static String playtime(double seconds) {
        long total = (long) seconds;
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        return hours > 0 ? hours + "h" + minutes + "min" : minutes + "min";
    }

    /** Nombre simple avec séparateur de milliers français (points). */
    public static String count(double value) {
        return formatNumber((long) value);
    }

    /** Nombre de blocs avec unité. */
    public static String blocks(double value) {
        long v = (long) value;
        return formatNumber(v) + " bloc" + (v > 1 ? "s" : "");
    }

    /** Distance stockée en centièmes de bloc → blocs entiers avec unité. */
    public static String distance(double hundredths) {
        long blocks = (long) (hundredths / 100);
        return formatNumber(blocks) + " blocs";
    }

    private static String formatNumber(long value) {
        String s = Long.toString(value);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            sb.append(s.charAt(i));
            count++;
            if (count % 3 == 0 && i > 0) {
                sb.append('.');
            }
        }
        return sb.reverse().toString();
    }
}
