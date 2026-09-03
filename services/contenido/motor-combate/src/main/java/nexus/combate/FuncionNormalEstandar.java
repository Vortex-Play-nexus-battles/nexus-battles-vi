package nexus.combate;

public final class FuncionNormalEstandar {

    private static final double A1 = 0.254829592;
    private static final double A2 = -0.284496736;
    private static final double A3 = 1.421413741;
    private static final double A4 = -1.453152027;
    private static final double A5 = 1.061405429;
    private static final double P = 0.3275911;

    private FuncionNormalEstandar() {
    }

    public static double cdf(double z) {
        return 0.5 * (1 + erf(z / Math.sqrt(2)));
    }

    private static double erf(double x) {
        double signo = x < 0 ? -1 : 1;
        double valorAbsoluto = Math.abs(x);

        double t = 1.0 / (1.0 + P * valorAbsoluto);
        double y = 1.0 - (((((A5 * t + A4) * t) + A3) * t + A2) * t + A1) * t * Math.exp(-valorAbsoluto * valorAbsoluto);

        return signo * y;
    }
}
