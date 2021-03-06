
package org.vniim.exactcoveragefactor;

import java.text.DecimalFormat;

import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;


public class KCalculator {

    private final double p0 = 0.95; // coverage probability

    private final int n;
    private final int n0;
    private final double a;

    private final double rho;
    private final double acos_rho;

    private final static DecimalFormat FMT = new DecimalFormat("#.####");

    public KCalculator(int n, int n0, double a) {

        this.n  = n;
        this.n0 = n0;
        this.a  = a;

        rho = calculateRho();
        acos_rho = Math.acos(rho);
    }

    private double calculateRho() {

        double r[] = new double[n0];
        r[0] = 1.;
        for (int i = 1; i < n0; ++i) { r[i] = a * r[i - 1]; }

        double vals[][] = new double[n0][n0];
        for (int i = 0; i < n0; ++i) {
            int k = 0;
            for (int j = i; j < n0; ++j) {
                vals[i][j] = r[k];
                vals[j][i] = r[k];
                ++k;
            }
        }

        RealMatrix V = MatrixUtils.createRealMatrix(vals);
        RealMatrix VInv = new LUDecomposition(V).getSolver().getInverse();

        vals = new double[n0][2];
        for (int i = 0; i < n0; ++i) {
            vals[i][0] = 1.;
            vals[i][1] = i;
        }

        RealMatrix A = MatrixUtils.createRealMatrix(vals);

        RealMatrix Prod = A.transpose().multiply(VInv).multiply(A);
        RealMatrix T = new LUDecomposition(Prod).getSolver().getInverse();

        vals = new double[][]{{1., 0.}};
        RealMatrix v1 = MatrixUtils.createRealMatrix(vals);

        vals = new double[][]{{1., n - 1.}};
        RealMatrix v2 = MatrixUtils.createRealMatrix(vals);

        double num = -v1.multiply(T).multiply(v2.transpose()).getEntry(0, 0);
        double den = Math.sqrt(
            v1.multiply(T).multiply(v1.transpose()).getEntry(0, 0) *
            v2.multiply(T).multiply(v2.transpose()).getEntry(0, 0)
        );

        return num / den;
    }

    private double G(double t) {

        double g = 1.;
        if ((t < 1.) && (t >= Math.sqrt(0.5 + 0.5 * rho))) {
            g = (acos_rho - 2. * Math.acos(t)) / Math.PI;
        }
        return g;
    }

    private double f2(double x, double r) {

        return G(x / r) * r * Math.exp(-0.5 * r * r);
    }

    private double F(double x) {

        double I1 = 1. - Math.exp(-0.5 * x * x);

        double I2 = 0;
        double r_max = x * Math.sqrt(2. / (1. + rho));
        double h = 1.e-5 * (r_max - x);

        double r = x;
        double f2_prev = f2(x, r);

        while (r <= r_max + 0.1 * h) {

            r += h;
            double f2 = f2(x, r);
            I2 += 0.5 * (f2_prev + f2);
            f2_prev = f2;
        }

        I2 *= h;

        return I1 + I2;
    }

    private double calculateK(double KLow, double KHigh) {

        double p = F(KLow);
        if (p > p0 + 1.e-3) { return Double.NaN; } // bad initial KLow (not enough "low")

        p = F(KHigh);
        if (p < p0 - 1.e-3) { return Double.NaN; } // bad initial KHigh (not enough "high")

        // bisection
        while (KHigh - KLow > 1.e-6) {

            double KMid = 0.5 * (KLow + KHigh);
            p = F(KMid);

            if (p > p0) {
                KHigh = KMid;
            } else {
                KLow = KMid;
            }
        }

        double K = 0.5 * (KLow + KHigh);

        // a rough check, just in case
        p = F(K);
        if (Math.abs(p - p0) > 1.e-3) {
            throw new RuntimeException("check failed, F(K) != P0, got " + p);
        }

        return K;
    }



    public static void main(String args[]) {

        int n = 100, n0 = 35;

        if (args.length == 2) {

            n = Integer.parseInt(args[0]);
            if (n < 2) { throw new RuntimeException("invalid n"); }

            n0 = Integer.parseInt(args[1]);
            if (n0 > n) { throw new RuntimeException("invalid n0"); }

        } else {

            System.out.println(">> using default n, n0 values");
        }

        System.out.println(">> n = " + n + ", n0 = " + n0 + ",  a / rho / K:");
        System.out.println();

        for (int i = 0; i < 10; ++i) {
            double a = 0.1 * i;
            KCalculator calc = new KCalculator(n, n0, a);
            double K95 = calc.calculateK(2., 3.);
            System.out.println(
                    FMT.format(a)        + "\t" +
                    FMT.format(calc.rho) + "\t" +
                    FMT.format(K95));
        }
    }
}
