package org.przybyl;

/// Please remember to run with a VM option
/// `--add-modules jdk.incubator.vector`
/// For more details, please see [JEP-489](https://openjdk.org/jeps/489).
public class Operations {
    public static void main(String[] args) {
        // Use a large array to ensure measurable performance differences.
        int size = 10_000_000;
        double[] a = new double[size];
        double[] b = new double[size];
        double[] c = new double[size];

        // Initialize arrays with random double values.
        for (int i = 0; i < size; i++) {
            a[i] = Math.random();
            b[i] = Math.random();
        }

        MathOps scalar = new Scalar();
        MathOps vector = new Vector();

        // Warm-up phase for JIT compilation.
        for (int i = 0; i < 5; i++) {
            scalar.dotProduct(a, b);
            scalar.add(a, b, c);
            scalar.euclideanNorm(a);
            vector.dotProduct(a, b);
            vector.add(a, b, c);
            vector.euclideanNorm(a);
        }

        // Benchmark dot product.
        long start = System.nanoTime();
        double scalarDot = scalar.dotProduct(a, b);
        long scalarDotTime = System.nanoTime() - start;
        System.out.printf("Scalar Dot Product: %f, Time: %.3f ms%n", scalarDot, scalarDotTime / 1e6);

        start = System.nanoTime();
        double vectorDot = vector.dotProduct(a, b);
        long vectorDotTime = System.nanoTime() - start;
        System.out.printf("Vector Dot Product: %f, Time: %.3f ms%n", vectorDot, vectorDotTime / 1e6);
        System.out.printf("Speedup (Dot): %.2f×%n", (double) scalarDotTime / vectorDotTime);

        // Benchmark element-wise addition.
        start = System.nanoTime();
        scalar.add(a, b, c);
        long scalarAddTime = System.nanoTime() - start;
        System.out.printf("Scalar Addition Time: %.3f ms%n", scalarAddTime / 1e6);

        start = System.nanoTime();
        vector.add(a, b, c);
        long vectorAddTime = System.nanoTime() - start;
        System.out.printf("Vector Addition Time: %.3f ms%n", vectorAddTime / 1e6);
        System.out.printf("Speedup (Addition): %.2f×%n", (double) scalarAddTime / vectorAddTime);

        // Benchmark Euclidean norm.
        start = System.nanoTime();
        double scalarNorm = scalar.euclideanNorm(a);
        long scalarNormTime = System.nanoTime() - start;
        System.out.printf("Scalar Euclidean Norm: %f, Time: %.3f ms%n", scalarNorm, scalarNormTime / 1e6);

        start = System.nanoTime();
        double vectorNorm = vector.euclideanNorm(a);
        long vectorNormTime = System.nanoTime() - start;
        System.out.printf("Vector Euclidean Norm: %f, Time: %.3f ms%n", vectorNorm, vectorNormTime / 1e6);
        System.out.printf("Speedup (Norm): %.2f×%n", (double) scalarNormTime / vectorNormTime);

    }
}

interface MathOps {
    double dotProduct(double[] a, double[] b);

    void add(double[] a, double[] b, double[] c);

    double euclideanNorm(double[] a);
}

class Scalar implements MathOps {
    // Scalar dot product implementation
    public double dotProduct(double[] a, double[] b) {
        double sum = 0f;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }


    // Scalar element-wise addition: c = a + b
    public void add(double[] a, double[] b, double[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    // Scalar Euclidean norm: ||a|| = sqrt(sum(a[i]^2))
    public double euclideanNorm(double[] a) {
        double sum = 0f;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * a[i];
        }
        return Math.sqrt(sum);
    }
}

class Vector implements MathOps {
    // Vector dot product implementation using Panama vectors
    public double dotProduct(double[] a, double[] b) {
        return 0;
    }

    // Vector element-wise addition: c = a + b
    public void add(double[] a, double[] b, double[] c) {

    }

    // Vector Euclidean norm: ||a|| = sqrt(sum(a[i]^2))
    public double euclideanNorm(double[] a) {
       return 0;
    }
}